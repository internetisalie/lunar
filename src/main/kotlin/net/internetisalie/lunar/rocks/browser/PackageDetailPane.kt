package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Desktop
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Plugins-idiom detail pane (design §2.6) replacing the raw-Swing `PackageDetailPanel`. A
 * [CardLayout] toggles Empty / Detail / Error / NoTree cards. The Detail card uses a [JBHtmlPane]
 * description (BUG-363 font parity), a clickable [JBList] of [DependencyRow] (BUG-368), a version
 * picker, and an inline Install/Uninstall/Update button with in-place progress. The Empty card is a
 * [JBPanelWithEmptyText] (BUG-367); the Error card renders a Configure link to the Toolchain page.
 *
 * EDT-only. Metadata is fetched on a pooled thread and marshalled back with a selection-staleness
 * guard (review finding #48): a slow response for A never overwrites B's details.
 */
class PackageDetailPane(
    private val project: Project,
    private val model: LuaRocksBrowserModel,
) : JPanel(CardLayout()), Disposable {

    /** Set by the panel: activating a dependency row searches that package (design §2.6). */
    var onDependencyClicked: (String) -> Unit = {}

    private val executor = LuaRocksInstallExecutor(project)
    private val cards = layout as CardLayout

    private val nameLabel = JBLabel().apply { font = font.deriveFont(font.size2D + 2f) }
    private val versionPicker = JComboBox<String>()
    private val description = JBHtmlPane().also { Disposer.register(this, it) }
    private val licenseLabel = JBLabel()
    private val homepageButton = linkButton()
    private val depsModel = DefaultListModel<DependencyRow>()
    private val depsList = JBList(depsModel)
    private val actionButton = JButton("Install")
    private val updateButton = JButton("Update").apply { isVisible = false }
    private val statusLabel = JBLabel().apply { border = JBUI.Borders.empty(2, 0) }

    private val emptyCard = JBPanelWithEmptyText().withEmptyText("No package selected")
    private val errorCard = ErrorCard(project)
    private val noTreeCard = JBPanelWithEmptyText()
        .withEmptyText("No project rock tree; initialize a LuaRocks project")

    private var currentRow: LuaRockRow? = null
    private var currentHomepage: String? = null
    private var selectionToken: Long = 0

    init {
        add(emptyCard, EMPTY)
        add(buildDetailCard(), DETAIL)
        add(errorCard, ERROR)
        add(noTreeCard, NO_TREE)
        wireDepsClick()
        homepageButton.addActionListener { openHomepage() }
        actionButton.addActionListener { onActionClicked() }
        updateButton.addActionListener { onUpdateClicked() }
        showEmpty()
    }

    // ── Public entry points ────────────────────────────────────────────────

    fun showPackage(row: LuaRockRow, versions: List<String>) {
        currentRow = row
        selectionToken += 1
        nameLabel.text = row.pkg.name
        versionPicker.model = DefaultComboBoxModel(versions.toTypedArray())
        versionPicker.selectedItem = row.pkg.version
        resetDetailBody()
        renderActionFor(row)
        cards.show(this, DETAIL)
        fetchMetadata(row, selectionToken)
    }

    fun showInstalled(row: InstalledRockRow) =
        showPackage(LuaRockRow(installedPackage(row), installed = true), listOf(row.version))

    fun showEmpty() {
        currentRow = null
        cards.show(this, EMPTY)
    }

    fun showError(message: String) {
        errorCard.setMessage(message)
        cards.show(this, ERROR)
    }

    fun showNoTree() = cards.show(this, NO_TREE)

    // ── Dependency parsing (TC-ROCKS-16-10) ──────────────────────────────────

    internal fun dependencyRows(meta: LuaRockMetadata): List<DependencyRow> =
        meta.dependencies.map { DependencyRow(it) }

    // ── Card construction ─────────────────────────────────────────────────

    private fun buildDetailCard(): JPanel {
        val header = JPanel(BorderLayout()).apply {
            add(nameLabel, BorderLayout.WEST)
            add(versionPicker, BorderLayout.EAST)
            border = JBUI.Borders.empty(4, 6)
        }
        val actions = JPanel(HorizontalLayout(6)).apply {
            add(actionButton); add(updateButton); add(statusLabel)
            border = JBUI.Borders.empty(4, 6)
        }
        return JPanel(BorderLayout()).apply {
            add(header, BorderLayout.NORTH)
            add(buildBody(), BorderLayout.CENTER)
            add(actions, BorderLayout.SOUTH)
            border = JBUI.Borders.empty(4)
        }
    }

    private fun buildBody(): JPanel = JPanel(BorderLayout(0, 4)).apply {
        add(ScrollPaneFactory.createScrollPane(description), BorderLayout.CENTER)
        add(buildMetaStrip(), BorderLayout.NORTH)
        add(buildDepsPane(), BorderLayout.SOUTH)
        border = JBUI.Borders.empty(0, 6)
    }

    private fun buildMetaStrip(): JPanel = JPanel(HorizontalLayout(8)).apply {
        add(JBLabel("License:")); add(licenseLabel)
        add(JBLabel("Homepage:")); add(homepageButton)
    }

    private fun buildDepsPane(): JPanel = JPanel(BorderLayout(0, 2)).apply {
        depsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        add(JBLabel("Dependencies:"), BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(depsList), BorderLayout.CENTER)
    }

    // ── Behavior ──────────────────────────────────────────────────────────

    private fun wireDepsClick() {
        depsList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount < 2) return
                depsList.selectedValue?.let { onDependencyClicked(it.packageName) }
            }
        })
    }

    private fun fetchMetadata(row: LuaRockRow, token: Long) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val meta = LuaRocksMetadataService.show(row.pkg.name, row.pkg.version, project)
            ApplicationManager.getApplication().invokeLater { applyMetadata(meta, token) }
        }
    }

    private fun applyMetadata(meta: LuaRockMetadata?, token: Long) {
        if (token != selectionToken) return
        if (meta == null) {
            description.text = UIUtil.toHtml("Could not load metadata.")
            return
        }
        description.text = describe(meta)
        licenseLabel.text = meta.license ?: "(unknown)"
        currentHomepage = meta.homepage
        homepageButton.text = meta.homepage ?: "(none)"
        depsModel.clear()
        dependencyRows(meta).forEach { depsModel.addElement(it) }
    }

    private fun describe(meta: LuaRockMetadata): String {
        val body = meta.detailed ?: meta.summary ?: "(no description)"
        return UIUtil.toHtml(body.replace("\n", "<br>"))
    }

    private fun onActionClicked() {
        val row = currentRow ?: return
        val treeRoot = LuaRocksInstallCommand.resolveTargetTree(project) ?: run {
            showNoTree()
            return
        }
        if (row.installed) runRemove(row, treeRoot) else runInstall(row, treeRoot)
    }

    private fun onUpdateClicked() {
        val row = currentRow ?: return
        val treeRoot = LuaRocksInstallCommand.resolveTargetTree(project) ?: run {
            showNoTree()
            return
        }
        beginProgress("Updating…")
        executor.install(InstallRequest(row.pkg.name, null, treeRoot)) { success ->
            endProgress(success, "Updated.", "Update failed.")
            if (success) model.onInstallSucceeded(row.pkg.name)
        }
    }

    private fun runInstall(row: LuaRockRow, treeRoot: java.nio.file.Path) {
        beginProgress("Installing…")
        val version = versionPicker.selectedItem as? String
        executor.install(InstallRequest(row.pkg.name, version, treeRoot)) { success ->
            endProgress(success, "Installed.", "Install failed.")
            if (success) model.onInstallSucceeded(row.pkg.name)
        }
    }

    private fun runRemove(row: LuaRockRow, treeRoot: java.nio.file.Path) {
        beginProgress("Removing…")
        executor.remove(row.pkg.name, treeRoot) { success ->
            endProgress(success, "Removed.", "Remove failed.")
            if (success) model.onRemoveSucceeded(row.pkg.name)
        }
    }

    private fun beginProgress(label: String) {
        statusLabel.text = label
        actionButton.isEnabled = false
    }

    private fun endProgress(success: Boolean, ok: String, fail: String) {
        statusLabel.text = if (success) ok else fail
        actionButton.isEnabled = true
        currentRow?.let { renderActionFor(it.copy(installed = if (success) !it.installed else it.installed)) }
    }

    private fun renderActionFor(row: LuaRockRow) {
        currentRow = row
        actionButton.text = if (row.installed) "Uninstall" else "Install"
        actionButton.isEnabled = true
        updateButton.isVisible = row.hasUpdate
        statusLabel.text = if (row.hasUpdate) "Update available" else ""
    }

    private fun resetDetailBody() {
        description.text = UIUtil.toHtml("Loading…")
        licenseLabel.text = ""
        homepageButton.text = "(loading)"
        currentHomepage = null
        depsModel.clear()
    }

    private fun openHomepage() {
        val url = currentHomepage ?: return
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    private fun installedPackage(row: InstalledRockRow) =
        LuaRockPackage(row.name, row.version, "installed", "", "", isInstalled = true)

    private fun linkButton() = JButton("(none)").apply {
        isBorderPainted = false
        isFocusPainted = false
        isContentAreaFilled = false
        horizontalAlignment = javax.swing.SwingConstants.LEFT
    }

    override fun dispose() = Unit

    private companion object {
        const val EMPTY = "empty"
        const val DETAIL = "detail"
        const val ERROR = "error"
        const val NO_TREE = "noTree"
    }
}

/** The Error card: a message plus a Configure link that opens the Toolchain settings page (design §3.5). */
private class ErrorCard(project: Project) : JPanel(BorderLayout()) {
    private val messageLabel = JBLabel()

    init {
        val configure = JButton("Configure").apply {
            addActionListener {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, LuaToolchainConfigurable::class.java)
            }
        }
        val content = JPanel(HorizontalLayout(8)).apply {
            add(messageLabel); add(configure)
        }
        add(content, BorderLayout.NORTH)
        border = JBUI.Borders.empty(8)
    }

    fun setMessage(message: String) {
        messageLabel.text = message
    }
}
