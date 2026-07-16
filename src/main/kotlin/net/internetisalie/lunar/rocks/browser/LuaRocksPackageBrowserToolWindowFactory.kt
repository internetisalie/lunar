package net.internetisalie.lunar.rocks.browser

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import net.internetisalie.lunar.lang.LuaIcons
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * Bottom tool window — the LuaRocks Package Browser (ROCKS-02).
 *
 * Layout: `SearchTextField` + `Alarm`-debounced search trigger, a [OnePixelSplitter] holding:
 * - left pane: [JBList] of [LuaRockPackage] (collapsed arch variants, ROCKS-02-01/02/03)
 * - right pane: [PackageDetailPanel] (metadata, install/uninstall, version picker)
 *
 * Search is fired on a pooled thread; results are pushed to the EDT (ROCKS-02-03).
 * A Refresh button calls [LuaRocksSearchCache.invalidateAll] then re-runs the last query.
 *
 * Tool-window id: **"LuaRocks Packages"** (registered in plugin.xml; distinct from the existing
 * ROCKS-03 dependency tree which uses id "LuaRocks").
 */
class LuaRocksPackageBrowserToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = PackageBrowserPanel(project)
        val content = com.intellij.ui.content.ContentFactory.getInstance()
            .createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    // ── Inner panel ──────────────────────────────────────────────────────────

    private class PackageBrowserPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
        private val searchField = SearchTextField(true)
        private val listModel = DefaultListModel<LuaRockPackage>()
        private val packageList = JBList(listModel).apply { cellRenderer = PackageCellRenderer() }
        private val detailPanel = PackageDetailPanel(project)
        private val statusLabel = JBLabel("").apply { border = JBUI.Borders.empty(2, 6) }

        /**
         * Alarm for 300 ms debounce on the search field. Parented to this panel (the tool window
         * content's disposer) — a non-Swing-thread [Alarm] requires a parent [Disposable].
         */
        private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

        /** Last executed query — kept so Refresh can re-run it. */
        @Volatile private var lastQuery: String = ""

        /**
         * All versions keyed by package name (populated alongside [listModel]).
         * Accessed only on the EDT.
         */
        private val versionsByName = mutableMapOf<String, MutableList<String>>()

        init {
            val toolbar = buildToolbar()
            val splitter = OnePixelSplitter(false, 0.38f).apply {
                firstComponent = ScrollPaneFactory.createScrollPane(packageList)
                secondComponent = detailPanel
            }

            add(toolbar, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)

            searchField.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) = scheduleSearch()
            })

            packageList.addListSelectionListener {
                if (it.valueIsAdjusting) return@addListSelectionListener
                val pkg = packageList.selectedValue
                if (pkg == null) {
                    detailPanel.showEmpty()
                } else {
                    val versions = versionsByName[pkg.name] ?: listOf(pkg.version)
                    detailPanel.showPackage(pkg, versions)
                }
            }
        }

        private fun buildToolbar(): JPanel {
            val refreshBtn = JButton(AllIcons.Actions.Refresh).apply {
                toolTipText = "Refresh"
                addActionListener {
                    LuaRocksSearchCache.invalidateAll()
                    lastQuery.takeIf { it.isNotBlank() }?.let { runSearch(it) }
                }
            }
            val toolbar = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(2, 4)
                add(refreshBtn, BorderLayout.WEST)
                add(searchField, BorderLayout.CENTER)
            }
            return toolbar
        }

        /** Schedule a search, cancelling any pending debounce request first. */
        private fun scheduleSearch() {
            alarm.cancelAllRequests()
            alarm.addRequest({ runSearch(searchField.text.trim()) }, 300)
        }

        /** Execute the search on a pooled thread and push results to the EDT. */
        private fun runSearch(query: String) {
            if (query.isBlank()) {
                ApplicationManager.getApplication().invokeLater { clearResults("Enter a package name to search.") }
                return
            }
            ApplicationManager.getApplication().invokeLater { statusLabel.text = "Searching…" }

            ApplicationManager.getApplication().executeOnPooledThread {
                lastQuery = query
                val results = LuaRocksSearchService.search(query, project)

                // Build the version map from the full (non-collapsed) perspective:
                // search() already collapses arch variants, but different version rows survive
                // as separate LuaRockPackage entries. Group them by name.
                val byName = LinkedHashMap<String, MutableList<LuaRockPackage>>()
                for (pkg in results) byName.getOrPut(pkg.name) { mutableListOf() }.add(pkg)

                ApplicationManager.getApplication().invokeLater {
                    listModel.clear()
                    versionsByName.clear()

                    if (results.isEmpty()) {
                        statusLabel.text = if (query.isNotBlank()) "No packages found for \"$query\"." else ""
                    } else {
                        // Show one row per (name, version) — already collapsed by search()
                        for (pkg in results) {
                            listModel.addElement(pkg)
                        }
                        // Populate version picker data: distinct versions per name, in list order
                        for ((name, pkgs) in byName) {
                            versionsByName[name] = pkgs.map { it.version }.distinct().toMutableList()
                        }
                        statusLabel.text = "${results.size} package(s) found."
                    }
                }
            }
        }

        private fun clearResults(status: String) {
            listModel.clear()
            versionsByName.clear()
            detailPanel.showEmpty()
            statusLabel.text = status
        }

        /** The parented [alarm] is disposed automatically as a child of this panel. */
        override fun dispose() {
            alarm.cancelAllRequests()
        }
    }

    // ── List cell renderer ───────────────────────────────────────────────────

    private class PackageCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val pkg = value as? LuaRockPackage
            if (pkg != null) {
                text = "${pkg.name}  ${pkg.version}${if (pkg.isInstalled) "  ✓" else ""}"
                icon = LuaIcons.ROCKET
            }
            return this
        }
    }
}
