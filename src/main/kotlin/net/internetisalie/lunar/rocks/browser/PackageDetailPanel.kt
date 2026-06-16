package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Desktop
import java.net.URI
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants

/**
 * Right-hand detail panel showing metadata for the selected [LuaRockPackage].
 *
 * Metadata is fetched on a pooled thread via [LuaRocksMetadataService]; the panel updates on
 * the EDT. The version picker (ROCKS-02-06) allows selecting a specific version; Install and
 * Uninstall buttons delegate to [LuaRocksActionHandler].
 */
class PackageDetailPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val nameLabel = JBLabel("", SwingConstants.LEFT).apply { font = font.deriveFont(font.size2D + 2f) }
    private val versionPicker = JComboBox<String>()
    private val summaryArea = JTextArea(4, 40).apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
    private val licenseLabel = JBLabel("")
    private val homepageButton = JButton("(none)").apply { isBorderPainted = false; isFocusPainted = false; isContentAreaFilled = false; horizontalAlignment = SwingConstants.LEFT }
    private val depsArea = JTextArea(4, 40).apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
    private val installButton = JButton("Install")
    private val uninstallButton = JButton("Uninstall")
    private val statusLabel = JBLabel("").apply { border = JBUI.Borders.empty(2, 0) }

    private var currentPackage: LuaRockPackage? = null
    private var allVersions: List<String> = emptyList()
    private var currentHomepage: String? = null

    init {
        val header = JPanel(BorderLayout()).apply {
            add(nameLabel, BorderLayout.WEST)
            add(versionPicker, BorderLayout.EAST)
            border = JBUI.Borders.empty(4, 6)
        }

        val infoPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(JBLabel("Summary:").apply { border = JBUI.Borders.emptyTop(4) })
            add(ScrollPaneFactory.createScrollPane(summaryArea))
            add(JBLabel("License:").apply { border = JBUI.Borders.emptyTop(6) })
            add(licenseLabel)
            add(JBLabel("Homepage:").apply { border = JBUI.Borders.emptyTop(6) })
            add(homepageButton)
            add(JBLabel("Dependencies:").apply { border = JBUI.Borders.emptyTop(6) })
            add(ScrollPaneFactory.createScrollPane(depsArea))
            border = JBUI.Borders.empty(0, 6)
        }

        val actions = JPanel().apply {
            add(installButton)
            add(uninstallButton)
            add(statusLabel)
            border = JBUI.Borders.empty(4, 6)
        }

        add(header, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(infoPanel), BorderLayout.CENTER)
        add(actions, BorderLayout.SOUTH)
        border = JBUI.Borders.empty(4)

        homepageButton.addActionListener {
            currentHomepage?.let { url ->
                runCatching { Desktop.getDesktop().browse(URI(url)) }
            }
        }

        installButton.addActionListener {
            val pkg = currentPackage ?: return@addActionListener
            val version = versionPicker.selectedItem as? String
            statusLabel.text = "Installing…"
            installButton.isEnabled = false
            uninstallButton.isEnabled = false
            LuaRocksActionHandler.install(project, pkg.name, version) { success ->
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = if (success) "Installed." else "Install failed."
                    installButton.isEnabled = true
                    uninstallButton.isEnabled = true
                }
            }
        }

        uninstallButton.addActionListener {
            val pkg = currentPackage ?: return@addActionListener
            statusLabel.text = "Removing…"
            installButton.isEnabled = false
            uninstallButton.isEnabled = false
            LuaRocksActionHandler.uninstall(project, pkg.name) { success ->
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = if (success) "Removed." else "Remove failed."
                    installButton.isEnabled = true
                    uninstallButton.isEnabled = true
                }
            }
        }

        showEmpty()
    }

    /**
     * Show detail for [pkg]. All available [versions] are loaded into the version picker.
     * Metadata is fetched asynchronously.
     */
    fun showPackage(pkg: LuaRockPackage, versions: List<String>) {
        currentPackage = pkg
        allVersions = versions
        nameLabel.text = pkg.name
        statusLabel.text = "Loading…"
        installButton.isEnabled = false
        uninstallButton.isEnabled = false

        versionPicker.removeAllItems()
        versions.forEach { versionPicker.addItem(it) }
        versionPicker.selectedItem = pkg.version

        summaryArea.text = ""
        licenseLabel.text = ""
        depsArea.text = ""
        currentHomepage = null
        homepageButton.text = "(loading)"

        ApplicationManager.getApplication().executeOnPooledThread {
            val meta = LuaRocksMetadataService.show(pkg.name, pkg.version)
            ApplicationManager.getApplication().invokeLater {
                if (meta == null) {
                    statusLabel.text = "Could not load metadata."
                    installButton.isEnabled = true
                    uninstallButton.isEnabled = true
                    return@invokeLater
                }
                summaryArea.text = meta.summary ?: ""
                licenseLabel.text = meta.license ?: "(unknown)"
                currentHomepage = meta.homepage
                homepageButton.text = meta.homepage ?: "(none)"
                depsArea.text = meta.dependencies.joinToString("\n").ifEmpty { "(none)" }
                statusLabel.text = if (pkg.isInstalled) "Installed" else ""
                installButton.isEnabled = true
                uninstallButton.isEnabled = pkg.isInstalled
            }
        }
    }

    /** Clear the panel when no package is selected. */
    fun showEmpty() {
        currentPackage = null
        nameLabel.text = "(no package selected)"
        versionPicker.removeAllItems()
        summaryArea.text = ""
        licenseLabel.text = ""
        homepageButton.text = "(none)"
        depsArea.text = ""
        statusLabel.text = ""
        installButton.isEnabled = false
        uninstallButton.isEnabled = false
    }
}
