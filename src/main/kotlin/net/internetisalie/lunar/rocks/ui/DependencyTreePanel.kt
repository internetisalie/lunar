package net.internetisalie.lunar.rocks.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.rocks.LuaRocksDependencyResolver
import net.internetisalie.lunar.rocks.VersionConflictEngine
import net.internetisalie.lunar.rocks.deps.DependencyNode
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JButton
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
 * a node inspector on the right, and a toolbar (refresh / expand / collapse / filter).
 *
 * Resolution runs on a pooled thread; the built model is published to the EDT. No hard refs to PSI
 * are retained — only the [Project] is held, and resolution takes it per call.
 */
class DependencyTreePanel(private val project: Project) : JPanel(BorderLayout()) {
    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Lua dependencies"))
    private val tree = Tree(treeModel).apply { isRootVisible = true; cellRenderer = DependencyCellRenderer() }
    private val inspector = DependencyInspectorPanel()
    private val filterField = JBTextField()
    private val statusLabel = JBLabel("")
    private var resolvedRoot: DependencyNode? = null

    init {
        add(buildToolbar(), BorderLayout.NORTH)
        val split = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            ScrollPaneFactory.createScrollPane(tree),
            inspector,
        ).apply { resizeWeight = 0.6 }
        add(split, BorderLayout.CENTER)
        add(statusLabel.apply { border = JBUI.Borders.empty(2, 6) }, BorderLayout.SOUTH)
        tree.addTreeSelectionListener { inspector.show(selectedNode()) }
        filterField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = rebuildTree()
        })
    }

    private fun buildToolbar(): Component {
        val toolbar = JPanel(BorderLayout())
        toolbar.add(JButton(AllIcons.Actions.Refresh).apply { addActionListener { refresh() } }, BorderLayout.WEST)
        val east = JPanel(BorderLayout())
        east.add(JButton(AllIcons.Actions.Expandall).apply {
            addActionListener { expandAll() }
        }, BorderLayout.WEST)
        east.add(JButton(AllIcons.Actions.Collapseall).apply {
            addActionListener { collapseAll() }
        }, BorderLayout.CENTER)
        east.add(filterField.apply { columns = 16 }, BorderLayout.EAST)
        toolbar.add(east, BorderLayout.EAST)
        return toolbar
    }

    /** Re-resolves the dependency graph on a pooled thread, then republishes the tree on the EDT. */
    fun refresh() {
        statusLabel.text = "Resolving dependencies…"
        ApplicationManager.getApplication().executeOnPooledThread {
            val root = LuaRocksDependencyResolver.resolve(project)?.also { VersionConflictEngine.annotate(it) }
            ApplicationManager.getApplication().invokeLater {
                resolvedRoot = root
                statusLabel.text = if (root == null) {
                    "No project rockspec found, or no Lua interpreter is configured."
                } else {
                    ""
                }
                rebuildTree()
            }
        }
    }

    private fun rebuildTree() {
        val root = resolvedRoot
        val swingRoot = DefaultMutableTreeNode(root ?: "Lua dependencies")
        if (root != null) {
            val filter = filterField.text.trim().lowercase()
            addChildren(swingRoot, root, filter, mutableSetOf())
        }
        treeModel.setRoot(swingRoot)
        expandAll()
    }

    private fun addChildren(parent: DefaultMutableTreeNode, node: DependencyNode, filter: String, seen: MutableSet<DependencyNode>) {
        if (!seen.add(node)) return
        for (child in node.children) {
            if (!matches(child, filter)) continue
            val swingChild = DefaultMutableTreeNode(child)
            parent.add(swingChild)
            addChildren(swingChild, child, filter, seen)
        }
    }

    private fun matches(node: DependencyNode, filter: String): Boolean {
        if (filter.isEmpty()) return true
        if (node.packageName.lowercase().contains(filter)) return true
        if (node.resolvedVersion?.raw?.lowercase()?.contains(filter) == true) return true
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
                text = buildString {
                    append(node.packageName)
                    node.resolvedVersion?.let { append(" ").append(it.raw) } ?: append(" (missing)")
                    if (node.isCycle) append(" (cycle)")
                }
            }
            return this
        }
    }
}
