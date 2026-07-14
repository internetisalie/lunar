package net.internetisalie.lunar.redis.functions.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.internetisalie.lunar.redis.connection.LuaRedisConnectionSettings
import net.internetisalie.lunar.redis.connection.LuaRedisServerConnection
import net.internetisalie.lunar.redis.functions.DriftStatus
import net.internetisalie.lunar.redis.functions.LuaRedisFunctionDrift
import net.internetisalie.lunar.redis.functions.LuaRedisFunctionsController
import net.internetisalie.lunar.redis.functions.RedisFunctionEntry
import net.internetisalie.lunar.redis.functions.RedisLibraryEntry
import net.internetisalie.lunar.redis.resp.RespValue
import net.internetisalie.lunar.util.LunarCoroutineScopeService
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

private val log = logger<LuaRedisFunctionsPanel>()

/**
 * The "Redis Functions" tool-window panel (design §2.10).
 *
 * Displays a connection selector and a tree of libraries → functions with flag and drift glyphs.
 * Per-library Deploy (FUNCTION LOAD REPLACE) and Delete (with confirmation) actions. Mirrors
 * [net.internetisalie.lunar.rocks.ui.DependencyTreePanel]: all network calls on the project's
 * pooled coroutine scope; model published to the EDT via [withContext(Dispatchers.EDT)].
 *
 * Holds only [Project]; no retained PSI/VFS/RespClient refs (engineering-contract §4).
 */
class LuaRedisFunctionsPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val controller = LuaRedisFunctionsController()
    private val scope get() = LunarCoroutineScopeService.getInstance(project).scope
    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Redis Functions"))
    private val tree = Tree(treeModel).apply {
        isRootVisible = true
        cellRenderer = FunctionsCellRenderer()
    }
    private val statusLabel = JBLabel("")
    private val connectionSelector = JComboBox<ConnectionItem>()
    private var loadedEntries: List<RedisLibraryEntry> = emptyList()
    private var localBodies: Map<String, String> = emptyMap()

    init {
        add(buildToolbar(), BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    private fun buildToolbar(): Component {
        val toolbar = JPanel(BorderLayout())
        toolbar.add(connectionSelector, BorderLayout.CENTER)
        val buttons = JPanel()
        buttons.add(JButton(AllIcons.Actions.Refresh).apply {
            toolTipText = "Refresh"
            addActionListener { refresh() }
        })
        buttons.add(JButton("Deploy").apply {
            addActionListener { onDeploy() }
        })
        buttons.add(JButton("Delete").apply {
            addActionListener { onDelete() }
        })
        toolbar.add(buttons, BorderLayout.EAST)
        return toolbar
    }

    /** Reloads the connection list and triggers a FUNCTION LIST WITHCODE refresh. */
    fun refresh() {
        reloadConnections()
        val connection = selectedConnection() ?: return
        loadFunctions(connection)
    }

    private fun reloadConnections() {
        val connections = LuaRedisConnectionSettings.getInstance(project).connections()
        val model = DefaultComboBoxModel(connections.map { ConnectionItem(it) }.toTypedArray())
        connectionSelector.model = model
    }

    private fun selectedConnection(): LuaRedisServerConnection? =
        (connectionSelector.selectedItem as? ConnectionItem)?.connection

    private fun selectedLibrary(): RedisLibraryEntry? {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return node.userObject as? RedisLibraryEntry
    }

    private fun loadFunctions(connection: LuaRedisServerConnection) {
        statusLabel.text = "Loading…"
        scope.launch {
            val result = runCatching { controller.list(connection, withCode = true) }
            withContext(Dispatchers.EDT) {
                result.fold(
                    onSuccess = { entries -> onEntriesLoaded(entries) },
                    onFailure = { ex -> onLoadError(ex) },
                )
            }
        }
    }

    private fun onEntriesLoaded(entries: List<RedisLibraryEntry>) {
        loadedEntries = entries
        statusLabel.text = if (entries.isEmpty()) "No libraries loaded." else ""
        rebuildTree(entries)
    }

    private fun onLoadError(ex: Throwable) {
        log.info("Redis Functions list failed: ${ex.message}")
        statusLabel.text = "Error: ${ex.message}"
        treeModel.setRoot(DefaultMutableTreeNode("Redis Functions"))
    }

    private fun rebuildTree(entries: List<RedisLibraryEntry>) {
        val root = DefaultMutableTreeNode("Redis Functions")
        for (entry in entries) {
            val libraryNode = DefaultMutableTreeNode(entry)
            for (fn in entry.functions) {
                libraryNode.add(DefaultMutableTreeNode(fn))
            }
            root.add(libraryNode)
        }
        treeModel.setRoot(root)
        expandAll()
    }

    private fun expandAll() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    private fun onDeploy() {
        val connection = selectedConnection() ?: return
        val entry = selectedLibrary() ?: run {
            Messages.showInfoMessage(project, "Select a library to deploy.", "Deploy")
            return
        }
        val localBody = localBodies[entry.name] ?: run {
            Messages.showInfoMessage(project, "No local file body found for '${entry.name}'.", "Deploy")
            return
        }
        val confirmed = Messages.showYesNoDialog(
            project,
            "Deploy local '${entry.name}' to the server (FUNCTION LOAD REPLACE)?",
            "Deploy Library",
            Messages.getQuestionIcon(),
        )
        if (confirmed != Messages.YES) return
        runPanelOperation(connection) { ctrl, conn -> ctrl.deploy(conn, localBody) }
    }

    private fun onDelete() {
        val connection = selectedConnection() ?: return
        val entry = selectedLibrary() ?: run {
            Messages.showInfoMessage(project, "Select a library to delete.", "Delete")
            return
        }
        val confirmed = Messages.showYesNoDialog(
            project,
            "Delete library '${entry.name}' from the server?",
            "Delete Library",
            Messages.getQuestionIcon(),
        )
        if (confirmed != Messages.YES) return
        runPanelOperation(connection) { ctrl, conn -> ctrl.delete(conn, entry.name) }
    }

    private fun runPanelOperation(
        connection: LuaRedisServerConnection,
        block: suspend (LuaRedisFunctionsController, LuaRedisServerConnection) -> RespValue,
    ) {
        statusLabel.text = "Working…"
        scope.launch {
            val result = runCatching { block(controller, connection) }
            withContext(Dispatchers.EDT) {
                result.fold(
                    onSuccess = { loadFunctions(connection) },
                    onFailure = { ex ->
                        log.info("Redis Functions operation failed: ${ex.message}")
                        statusLabel.text = "Error: ${ex.message}"
                    },
                )
            }
        }
    }

    /** Sets the map of library-name → local source body used for drift comparison and deploy. */
    fun setLocalBodies(bodies: Map<String, String>) {
        localBodies = bodies
    }

    // -----------------------------------------------------------------------
    // Cell renderer
    // -----------------------------------------------------------------------

    private inner class FunctionsCellRenderer : DefaultTreeCellRenderer() {
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
            val node = (value as? DefaultMutableTreeNode)?.userObject ?: return this
            when (node) {
                is RedisLibraryEntry -> renderLibrary(node)
                is RedisFunctionEntry -> renderFunction(node)
            }
            return this
        }

        private fun renderLibrary(entry: RedisLibraryEntry) {
            val localBody = localBodies[entry.name]
            val drift = if (localBody != null) {
                LuaRedisFunctionDrift.compare(entry.libraryCode, localBody)
            } else {
                DriftStatus.UNKNOWN
            }
            icon = if (drift == DriftStatus.DRIFTED) AllIcons.General.Warning else AllIcons.Nodes.Package
            val driftLabel = if (drift == DriftStatus.DRIFTED) " [DRIFTED]" else ""
            text = "${entry.name}$driftLabel"
        }

        private fun renderFunction(fn: RedisFunctionEntry) {
            icon = AllIcons.Nodes.Method
            val flagStr = if (fn.flags.isNotEmpty()) " [${fn.flags.sorted().joinToString(", ")}]" else ""
            text = "${fn.name}$flagStr"
        }
    }

    // -----------------------------------------------------------------------
    // Connection selector model item
    // -----------------------------------------------------------------------

    private data class ConnectionItem(val connection: LuaRedisServerConnection) {
        override fun toString(): String = connection.name
    }
}
