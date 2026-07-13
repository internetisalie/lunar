package net.internetisalie.lunar.redis.debug

import com.intellij.icons.AllIcons
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.intellij.xdebugger.frame.presentation.XValuePresentation

/**
 * An [XNamedValue] rendering an LDB local / table entry (design §2.12).
 *
 * A [LdbValueNode.Scalar] presents inline (with a truncation suffix when the server cut the repr at
 * its `maxlen`); a [LdbValueNode.Table] presents as an expandable node whose children are the table
 * entries, each a nested [LuaLdbValue] built from the parsed value tree (design §3.4). Structural
 * only — no live-session references.
 */
class LuaLdbValue(name: String, private val node: LdbValueNode) : XNamedValue(name) {

    constructor(local: LuaLdbLocal) : this(local.name, local.value)

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val hasChildren = this.node is LdbValueNode.Table
        node.setPresentation(iconFor(this.node), presentationFor(this.node), hasChildren)
    }

    override fun computeChildren(node: XCompositeNode) {
        val table = this.node as? LdbValueNode.Table ?: run {
            super.computeChildren(node)
            return
        }
        val children = XValueChildrenList(table.entries.size)
        table.entries.forEach { (key, value) -> children.add(key, LuaLdbValue(key, value)) }
        node.addChildren(children, !table.truncated)
    }

    private fun iconFor(value: LdbValueNode) =
        if (value is LdbValueNode.Table) AllIcons.Nodes.Field else AllIcons.Nodes.Variable

    private fun presentationFor(value: LdbValueNode): XValuePresentation = when (value) {
        is LdbValueNode.Scalar -> scalarPresentation(value)
        is LdbValueNode.Table -> XRegularValuePresentation(tableSummary(value), "table")
    }

    private fun scalarPresentation(scalar: LdbValueNode.Scalar): XValuePresentation {
        val text = if (scalar.truncated) "${scalar.text} …" else scalar.text
        return XRegularValuePresentation(text, null)
    }

    private fun tableSummary(table: LdbValueNode.Table): String {
        val suffix = if (table.truncated) ", …" else ""
        return "{${table.entries.size} entries$suffix}"
    }
}
