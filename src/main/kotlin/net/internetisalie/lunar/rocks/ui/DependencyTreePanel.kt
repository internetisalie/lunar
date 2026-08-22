package net.internetisalie.lunar.rocks.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.rocks.LuaRocksDependencyResolver
import net.internetisalie.lunar.rocks.VersionConflictEngine
import net.internetisalie.lunar.rocks.deps.DependencyNode
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTree
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * The LuaRocks dependency tool-window panel: a dependency tree (with conflict markers) on the left,
 * a node inspector on the right, and a header carrying a flat [ActionToolbar] (refresh / expand /
 * collapse) beside a [SearchTextField] filter — the idiom every native tool window uses (BUG-448
 * #12, #13).
 *
 * Resolution runs on a pooled thread; the built model is published to the EDT. No hard refs to PSI
 * are retained — only the [Project] is held, and resolution takes it per call.
 */
class DependencyTreePanel(
    private val project: Project,
) : JPanel(BorderLayout()) {
    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Lua dependencies"))
    private val tree =
        Tree(treeModel).apply {
            isRootVisible = true
            cellRenderer = DependencyCellRenderer()
        }
    private val inspector = DependencyInspectorPanel()
    private val filterField =
        SearchTextField(false).apply {
            textEditor.columns = 16
            textEditor.emptyText.text = "Filter dependencies"
        }
    private val statusLabel = JBLabel("")
    private var resolvedRoots: List<DependencyNode> = emptyList()

    init {
        add(buildHeader(), BorderLayout.NORTH)
        val split =
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                ScrollPaneFactory.createScrollPane(tree),
                inspector,
            ).apply { resizeWeight = 0.6 }
        add(split, BorderLayout.CENTER)
        add(statusLabel.apply { border = JBUI.Borders.empty(2, 6) }, BorderLayout.SOUTH)
        tree.addTreeSelectionListener { inspector.show(selectedNode()) }
        filterField.addDocumentListener(
            object : DocumentAdapter() {
                override fun textChanged(event: DocumentEvent) = rebuildTree()
            },
        )
    }

    private fun buildHeader(): JComponent {
        val header = JPanel(BorderLayout())
        header.add(buildActionToolbar().component, BorderLayout.WEST)
        header.add(filterField, BorderLayout.EAST)
        return header
    }

    /** The flat action gutter. Constructed on the EDT with the tree as its data-context target. */
    private fun buildActionToolbar(): ActionToolbar {
        val group =
            DefaultActionGroup(
                PanelAction("Refresh", AllIcons.Actions.Refresh) { refresh() },
                PanelAction("Expand All", AllIcons.Actions.Expandall) { expandAll() },
                PanelAction("Collapse All", AllIcons.Actions.Collapseall) { collapseAll() },
            )
        return ActionManager
            .getInstance()
            .createActionToolbar(TOOLBAR_PLACE, group, true)
            .also { it.targetComponent = tree }
    }

    /** Re-resolves the dependency graph on a pooled thread, then republishes the tree on the EDT. */
    fun refresh() {
        statusLabel.text = "Resolving dependencies…"
        ApplicationManager.getApplication().executeOnPooledThread {
            val roots =
                LuaRocksDependencyResolver
                    .resolveAll(project)
                    .onEach { VersionConflictEngine.annotate(it) }
            ApplicationManager.getApplication().invokeLater {
                resolvedRoots = roots
                statusLabel.text =
                    if (roots.isEmpty()) {
                        "No project rockspec found, or no Lua interpreter is configured."
                    } else {
                        ""
                    }
                rebuildTree()
            }
        }
    }

    private fun rebuildTree() {
        val swingRoot = DefaultMutableTreeNode("Lua dependencies")
        val filter = filterField.text.trim().lowercase()
        for (root in resolvedRoots) {
            if (!matches(root, filter)) continue
            val swingChild = DefaultMutableTreeNode(root)
            swingRoot.add(swingChild)
            addChildren(swingChild, root, filter, mutableSetOf())
        }
        treeModel.setRoot(swingRoot)
        expandAll()
    }

    private fun addChildren(
        parent: DefaultMutableTreeNode,
        node: DependencyNode,
        filter: String,
        seen: MutableSet<DependencyNode>,
    ) {
        if (!seen.add(node)) return
        for (child in node.children) {
            if (!matches(child, filter)) continue
            val swingChild = DefaultMutableTreeNode(child)
            parent.add(swingChild)
            addChildren(swingChild, child, filter, seen)
        }
    }

    private fun matches(
        node: DependencyNode,
        filter: String,
    ): Boolean {
        if (filter.isEmpty()) return true
        if (node.packageName.lowercase().contains(filter)) return true
        if (node.resolvedVersion
                ?.raw
                ?.lowercase()
                ?.contains(filter) == true
        ) {
            return true
        }
        return node.children.any { matches(it, filter) }
    }

    private fun selectedNode(): DependencyNode? =
        (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? DependencyNode

    private fun expandAll() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    private fun collapseAll() {
        var row = tree.rowCount - 1
        while (row >= 0) {
            tree.collapseRow(row)
            row--
        }
        (treeModel.root as? DefaultMutableTreeNode)?.let { tree.expandPath(TreePath(it.path)) }
    }

    /** A toolbar entry that delegates to a panel method; icon-only, with its text as the tooltip. */
    private class PanelAction(
        text: String,
        icon: Icon,
        private val perform: () -> Unit,
    ) : AnAction(text, text, icon),
        DumbAware {
        override fun actionPerformed(event: AnActionEvent) = perform()
    }

    /** Renders dependency nodes with the rocket icon and a warning overlay for conflicts. */
    private class DependencyCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ): Component {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
            val node = (value as? DefaultMutableTreeNode)?.userObject as? DependencyNode
            if (node != null) {
                icon = if (node.hasConflicts) AllIcons.General.Warning else LuaIcons.ROCKET
                text =
                    buildString {
                        append(node.packageName)
                        node.resolvedVersion?.let { append(" ").append(it.raw) } ?: append(" (missing)")
                        if (node.isCycle) append(" (cycle)")
                    }
            }
            return this
        }
    }

    private companion object {
        const val TOOLBAR_PLACE = "LunarRocksDependenciesToolbar"
    }
}
