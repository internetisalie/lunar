package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import net.internetisalie.lunar.lang.LuaIcons
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.event.DocumentEvent

/**
 * Two-tab LuaRocks browser panel in the Plugins idiom (ROCKS-16-01, design §2.7). A [JBTabbedPane]
 * holds a Marketplace tab (debounced `SearchTextField` → search rows) and an Installed tab
 * (zero-query list from the canonical tree), each an [OnePixelSplitter] list-over-detail split over
 * its **own** [PackageDetailPane]. A north strip shows the active target-tree path.
 *
 * EDT-confined. All state flows through [LuaRocksBrowserModel]; this panel is its [Listener]. The
 * 300 ms search [Alarm] and both [PackageDetailPane]s are parented to this [Disposable] (BUG-379).
 */
class LuaRocksBrowserPanel internal constructor(
    private val project: Project,
    private val treeInitializer: LuaRocksTreeInitializer,
    backend: LuaRocksBrowserBackend,
) : JBPanel<LuaRocksBrowserPanel>(BorderLayout()),
    Disposable,
    LuaRocksBrowserModel.Listener {
    constructor(project: Project) : this(project, LuaRocksTreeInitializer.forProject(project), ProjectBackend(project))

    private val model = LuaRocksBrowserModel(backend, this)
    private val marketDetail = PackageDetailPane(project, model).also { Disposer.register(this, it) }
    private val installedDetail = PackageDetailPane(project, model).also { Disposer.register(this, it) }
    private val searchField = SearchTextField(true)
    private val marketModel = DefaultListModel<LuaRockRow>()
    private val marketList = JBList(marketModel).apply { cellRenderer = MarketCellRenderer() }
    private val installedModel = DefaultListModel<InstalledRockRow>()
    private val installedList = JBList(installedModel).apply { cellRenderer = InstalledCellRenderer() }
    private val treeLabel = JBLabel()
    internal val initializeLink = ActionLink(PackageDetailPane.INITIALIZE_TREE) { initializeTree() }
    private val treeStrip =
        JBPanel<JBPanel<*>>(HorizontalLayout(JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(2, 6)
            add(treeLabel)
            add(initializeLink)
        }
    private val tabs = JBTabbedPane()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    init {
        detailPanes().forEach {
            it.onDependencyClicked = { name -> searchFor(name) }
            it.onInitializeTree = { initializeTree() }
        }
        tabs.addTab("Marketplace", buildMarketplaceTab())
        tabs.addTab("Installed", buildInstalledTab())
        tabs.addChangeListener { onTabChanged() }
        add(treeStrip, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)
        wireSelectionListeners()
        refreshTreeStrip()
        model.loadPopular()
    }

    // ── LuaRocksBrowserModel.Listener ──────────────────────────────────────

    override fun onState(state: BrowserState) {
        when (state) {
            is BrowserState.Results -> renderResults(state.rows)
            is BrowserState.Installed -> renderInstalled(state.rows)
            is BrowserState.Error -> showError(state.message)
            is BrowserState.NoTree -> detailPanes().forEach { it.showNoTree() }
            BrowserState.Idle -> showSearchPrompt()
            BrowserState.Loading -> Unit
        }
    }

    override fun onRowChanged(index: Int) {
        if (index in 0 until marketModel.size()) marketModel.set(index, model.currentRows[index])
    }

    // ── Tab construction ───────────────────────────────────────────────────

    private fun buildMarketplaceTab(): Component {
        searchField.addDocumentListener(
            object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) = scheduleSearch()
            },
        )
        val top =
            JBPanel<JBPanel<*>>(BorderLayout()).apply {
                border = JBUI.Borders.empty(2, 4)
                add(searchField, BorderLayout.CENTER)
            }
        val left =
            JBPanel<JBPanel<*>>(BorderLayout()).apply {
                add(top, BorderLayout.NORTH)
                add(ScrollPaneFactory.createScrollPane(marketList), BorderLayout.CENTER)
            }
        return splitter(left, marketDetail)
    }

    private fun buildInstalledTab(): Component =
        splitter(ScrollPaneFactory.createScrollPane(installedList), installedDetail)

    /**
     * Each tab gets its **own** detail pane. A Swing component has one parent, so handing one shared
     * pane to both splitters re-parents it out of the first and leaves that tab's detail half blank
     * (BUG-449). The panes are independent — [PackageDetailPane] calls into the model but never
     * subscribes to it — so there is nothing to keep in sync.
     */
    private fun splitter(
        left: Component,
        detail: PackageDetailPane,
    ): OnePixelSplitter =
        OnePixelSplitter(false, 0.38f).apply {
            firstComponent = left as? javax.swing.JComponent
            secondComponent = detail
        }

    private fun detailPanes(): List<PackageDetailPane> = listOf(marketDetail, installedDetail)

    // ── Behavior ───────────────────────────────────────────────────────────

    private fun wireSelectionListeners() {
        marketList.addListSelectionListener {
            if (it.valueIsAdjusting) return@addListSelectionListener
            val row = marketList.selectedValue ?: return@addListSelectionListener marketDetail.showEmpty()
            marketDetail.showPackage(row, row.versions.ifEmpty { listOf(row.pkg.version) })
        }
        installedList.addListSelectionListener {
            if (it.valueIsAdjusting) return@addListSelectionListener
            installedList.selectedValue?.let { row -> showInstalledRow(row) }
        }
    }

    private fun onTabChanged() {
        if (tabs.selectedIndex == INSTALLED_TAB) model.loadInstalled()
    }

    private fun scheduleSearch() {
        alarm.cancelAllRequests()
        alarm.addRequest({ model.runMarketplaceSearch(searchField.text.trim()) }, DEBOUNCE_MS)
    }

    private fun searchFor(name: String) {
        tabs.selectedIndex = MARKETPLACE_TAB
        searchField.text = name
        model.runMarketplaceSearch(name)
    }

    /** An error replaces the previous results, which no longer answer the query (BUG-69). */
    private fun showError(message: String) {
        marketModel.clear()
        marketList.emptyText.text = ""
        detailPanes().forEach { it.showError(message) }
    }

    /** The zero-query state when no popular list could be loaded (BUG-56). */
    private fun showSearchPrompt() {
        marketModel.clear()
        marketList.emptyText.text = SEARCH_PROMPT
    }

    private fun renderResults(rows: List<LuaRockRow>) {
        marketList.emptyText.text = NO_RESULTS
        marketModel.clear()
        rows.forEach { marketModel.addElement(it) }
    }

    private fun renderInstalled(rows: List<InstalledRockRow>) {
        val selected = installedList.selectedValue
        installedModel.clear()
        rows.forEach { installedModel.addElement(it) }
        if (selected == null) return
        // Re-select by name: an update changes the version, not the rock (BUG-66).
        rows.firstOrNull { it.name == selected.name }?.let { installedList.setSelectedValue(it, true) }
            ?: installedDetail.showEmpty()
    }

    /** Shows an installed rock, then offers its newer versions once the server answers (BUG-65). */
    private fun showInstalledRow(row: InstalledRockRow) {
        installedDetail.showInstalled(row)
        model.availableVersions(row.name) { versions -> installedDetail.showAvailableVersions(row, versions) }
    }

    internal fun treeStripText(): String = treeLabel.text

    internal fun marketplaceNames(): List<String> = (0 until marketModel.size()).map { marketModel[it].pkg.name }

    internal fun marketplaceEmptyText(): String = marketList.emptyText.text

    private fun refreshTreeStrip() {
        val tree = LuaRocksInstallCommand.resolveTargetTree(project)
        treeLabel.text = tree?.let { "Target tree: $it" } ?: "No project rock tree"
        initializeLink.isVisible = tree == null
    }

    /** Runs `luarocks init` for the project, then re-reads the tree it created (BUG-55). */
    private fun initializeTree() {
        initializeLink.isEnabled = false
        treeInitializer.initialize { success ->
            initializeLink.isEnabled = true
            refreshTreeStrip()
            if (success) onTreeCreated()
        }
    }

    private fun onTreeCreated() {
        detailPanes().forEach { it.showEmpty() }
        if (tabs.selectedIndex == INSTALLED_TAB) model.loadInstalled()
    }

    override fun dispose() {
        alarm.cancelAllRequests()
    }

    private class MarketCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            (value as? LuaRockRow)?.let { row ->
                val badge = if (row.installed) "  ✓" else ""
                val update = if (row.hasUpdate) "  ⬆" else ""
                text =
                    listOf(row.pkg.name, row.pkg.version).filter { it.isNotBlank() }.joinToString("  ") + badge + update
                icon = LuaIcons.ROCKSPEC
            }
            return this
        }
    }

    /** An installed rock as `name  version`, not its data-class text (BUG-64). */
    private class InstalledCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            (value as? InstalledRockRow)?.let { row ->
                text = installedRowText(row)
                icon = LuaIcons.ROCKSPEC
            }
            return this
        }
    }

    private companion object {
        const val MARKETPLACE_TAB = 0
        const val INSTALLED_TAB = 1
        const val DEBOUNCE_MS = 300
        const val SEARCH_PROMPT = "Search luarocks.org"
        const val NO_RESULTS = "No packages found"
    }
}

/** The Installed tab's text for a rock (BUG-64). */
internal fun installedRowText(row: InstalledRockRow): String = "${row.name}  ${row.version}"
