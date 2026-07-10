package net.internetisalie.lunar.redis.console

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.treeStructure.Tree
import net.internetisalie.lunar.redis.resp.RespValue
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Renders a [RespValue] reply as an expandable tree plus a text console (design §2.6, §3.4, §3.5).
 *
 * Arrays/maps populate the [Tree] component; scalar replies, errors, and server process output go to
 * the wrapped text [ConsoleView] (built via [TextConsoleBuilderFactory]). [showError] prints the RESP
 * error **class** tag (`(error) WRONGTYPE …`, TC-CON-2). Tree/console mutations must run on the EDT —
 * the profile state marshals [showReply]/[showError] with `withContext(Dispatchers.EDT)` (design §2.6).
 * Holds no hard `Editor`/`PsiFile`/`VirtualFile` field; the text console is a platform-managed
 * [com.intellij.openapi.Disposable]. Implements [ExecutionConsole] so it can be wrapped directly in a
 * `DefaultExecutionResult` (design §5).
 */
class RespReplyTreeConsole(project: Project) : ExecutionConsole {

    private val console: ConsoleView =
        TextConsoleBuilderFactory.getInstance().createBuilder(project).console

    private val treeRoot = DefaultMutableTreeNode("(no reply)")
    private val treeModel = DefaultTreeModel(treeRoot)
    private val tree = Tree(treeModel)

    /** The console component: reply tree above the text console. Add it to the run content UI. */
    private val rootComponent: JComponent = OnePixelSplitter(true, 0.5f).apply {
        firstComponent = com.intellij.ui.components.JBScrollPane(tree)
        secondComponent = console.component
    }

    init {
        Disposer.register(this, console)
    }

    override fun getComponent(): JComponent = rootComponent

    override fun getPreferredFocusableComponent(): JComponent = console.preferredFocusableComponent

    /** Adds a console message [filter] (e.g. the error-link filter). Call before printing. */
    fun addFilter(filter: Filter) {
        console.addMessageFilter(filter)
    }

    /** Renders [reply]: arrays/maps into the tree, scalars inline in the text console (design §3.5). Call on the EDT. */
    fun showReply(reply: RespValue) {
        when (reply) {
            is RespValue.Error -> showError(reply)
            is RespValue.Array, is RespValue.Map -> populateTree(reply)
            else -> printReplyLine(reply)
        }
    }

    private fun populateTree(reply: RespValue) {
        val root = RespReplyTreeModel.build(reply)
        treeRoot.userObject = root.label
        treeRoot.removeAllChildren()
        root.children.forEach { treeRoot.add(swingNodeFor(it)) }
        treeModel.reload()
        expandRoot()
    }

    private fun swingNodeFor(node: RespReplyNode): DefaultMutableTreeNode {
        val swingNode = DefaultMutableTreeNode(node.label)
        node.children.forEach { swingNode.add(swingNodeFor(it)) }
        return swingNode
    }

    private fun expandRoot() {
        tree.expandRow(0)
    }

    private fun printReplyLine(reply: RespValue) {
        console.print("${RespReplyTreeModel.build(reply).label}\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }

    /** Prints an error line carrying the server error **class** tag (design §3.4, TC-CON-2). Call on the EDT. */
    fun showError(error: RespValue.Error) {
        console.print("${formatError(error)}\n", ConsoleViewContentType.ERROR_OUTPUT)
    }

    /** Streams a launched server's stdout/stderr into the text console (design §2.6). */
    fun attachProcessOutput(handler: ProcessHandler) {
        console.attachToProcess(handler)
    }

    override fun dispose() {
        // console is disposed via Disposer registration.
    }

    companion object {
        /** Formats an error line with the server error **class** tag: `(error) <klass> <message>` (design §3.4). */
        fun formatError(error: RespValue.Error): String = "(error) ${error.klass} ${error.message}".trim()
    }
}
