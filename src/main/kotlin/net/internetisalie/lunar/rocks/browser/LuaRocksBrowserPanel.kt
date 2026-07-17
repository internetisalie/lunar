package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
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
 * (zero-query list from the canonical tree), each an [OnePixelSplitter] list-over-detail split that
 * reuses one shared [PackageDetailPane]. A north strip shows the active target-tree path.
 *
 * EDT-confined. All state flows through [LuaRocksBrowserModel]; this panel is its [Listener]. The
 * 300 ms search [Alarm] and the [PackageDetailPane] are parented to this [Disposable] (BUG-379).
 */
class LuaRocksBrowserPanel(private val project: Project) :
    JBPanel<LuaRocksBrowserPanel>(BorderLayout()), Disposable, LuaRocksBrowserModel.Listener {

    private val model = LuaRocksBrowserModel(ProjectBackend(project), this)
    private val detailPane = PackageDetailPane(project, model).also { Disposer.register(this, it) }
    private val searchField = SearchTextField(true)
    private val marketModel = DefaultListModel<LuaRockRow>()
    private val marketList = JBList(marketModel).apply { cellRenderer = MarketCellRenderer() }
    private val installedModel = DefaultListModel<InstalledRockRow>()
    private val installedList = JBList(installedModel)
    private val treeStrip = JBLabel().apply { border = JBUI.Borders.empty(2, 6) }
    private val tabs = JBTabbedPane()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    init {
        detailPane.onDependencyClicked = { name -> searchFor(name) }
        tabs.addTab("Marketplace", buildMarketplaceTab())
        tabs.addTab("Installed", buildInstalledTab())
        tabs.addChangeListener { onTabChanged() }
        add(treeStrip, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)
        wireSelectionListeners()
        refreshTreeStrip()
    }

    // ── LuaRocksBrowserModel.Listener ──────────────────────────────────────

    override fun onState(state: BrowserState) {
        when (state) {
            is BrowserState.Results -> renderResults(state.rows)
            is BrowserState.Installed -> renderInstalled(state.rows)
            is BrowserState.Error -> detailPane.showError(state.message)
            is BrowserState.NoTree -> detailPane.showNoTree()
            BrowserState.Idle -> marketModel.clear()
            BrowserState.Loading -> Unit
        }
    }

    override fun onRowChanged(index: Int) {
        if (index in 0 until marketModel.size()) marketModel.set(index, model.currentRows[index])
    }

    // ── Tab construction ───────────────────────────────────────────────────

    private fun buildMarketplaceTab(): Component {
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = scheduleSearch()
        })
        val top = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 4)
            add(searchField, BorderLayout.CENTER)
        }
        val left = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(top, BorderLayout.NORTH)
            add(ScrollPaneFactory.createScrollPane(marketList), BorderLayout.CENTER)
        }
        return splitter(left)
    }

    private fun buildInstalledTab(): Component =
        splitter(ScrollPaneFactory.createScrollPane(installedList))

    private fun splitter(left: Component): OnePixelSplitter = OnePixelSplitter(false, 0.38f).apply {
        firstComponent = left as? javax.swing.JComponent
        secondComponent = detailPane
    }

    // ── Behavior ───────────────────────────────────────────────────────────

    private fun wireSelectionListeners() {
        marketList.addListSelectionListener {
            if (it.valueIsAdjusting) return@addListSelectionListener
            val row = marketList.selectedValue ?: return@addListSelectionListener detailPane.showEmpty()
            detailPane.showPackage(row, listOf(row.pkg.version))
        }
        installedList.addListSelectionListener {
            if (it.valueIsAdjusting) return@addListSelectionListener
            installedList.selectedValue?.let { row -> detailPane.showInstalled(row) }
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

    private fun renderResults(rows: List<LuaRockRow>) {
        marketModel.clear()
        rows.forEach { marketModel.addElement(it) }
    }

    private fun renderInstalled(rows: List<InstalledRockRow>) {
        installedModel.clear()
        rows.forEach { installedModel.addElement(it) }
    }

    private fun refreshTreeStrip() {
        val tree = LuaRocksInstallCommand.resolveTargetTree(project)
        treeStrip.text = tree?.let { "Target tree: $it" } ?: "No project rock tree"
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
                text = "${row.pkg.name}  ${row.pkg.version}$badge$update"
                icon = LuaIcons.ROCKET
            }
            return this
        }
    }

    private companion object {
        const val MARKETPLACE_TAB = 0
        const val INSTALLED_TAB = 1
        const val DEBOUNCE_MS = 300
    }
}
