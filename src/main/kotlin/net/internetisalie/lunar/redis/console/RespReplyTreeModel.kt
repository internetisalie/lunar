package net.internetisalie.lunar.redis.console

import net.internetisalie.lunar.redis.resp.RespValue
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel

/**
 * One node in the reply tree: a [label] rendered inline plus its (possibly empty) children (design §3.5).
 *
 * Scalars are leaves ([children] empty, [expandable] false); arrays/maps are expandable nodes with one
 * child per element. Immutable — built off the EDT and handed to the Swing [TreeModel] on the EDT.
 */
data class RespReplyNode(
    val label: String,
    val expandable: Boolean,
    val children: List<RespReplyNode>,
)

/**
 * Shapes a decoded [RespValue] into a Swing [TreeModel] for the reply-tree console (design §2.6, §3.5).
 *
 * Scalars (`Simple`/`Integer`/`Bulk`/`Double`/`Bool`/`Null`) become a single inline leaf; `Array`/`Map`
 * become an expandable node with one child per element (map child label `key = value`); nested
 * arrays/maps recurse. `Bulk(null)`/`Array(null)` render as `(nil)`. No truncation (LDB `maxlen` is
 * REDIS-02). Pure — thread-agnostic.
 */
object RespReplyTreeModel {

    /** Builds an immutable [RespReplyNode] tree for [reply] (design §3.5). */
    fun build(reply: RespValue): RespReplyNode = nodeFor(index = null, value = reply)

    /** Builds a Swing [TreeModel] from [reply] for direct display in a `Tree` component. */
    fun buildSwingModel(reply: RespValue): TreeModel = DefaultTreeModel(swingNodeFor(build(reply)))

    private fun swingNodeFor(node: RespReplyNode): DefaultMutableTreeNode {
        val swingNode = DefaultMutableTreeNode(node.label)
        node.children.forEach { swingNode.add(swingNodeFor(it)) }
        return swingNode
    }

    private fun nodeFor(index: Int?, value: RespValue): RespReplyNode = when (value) {
        is RespValue.Array -> arrayNode(index, value)
        is RespValue.Map -> mapNode(index, value)
        else -> RespReplyNode(labelFor(index, scalarText(value)), expandable = false, children = emptyList())
    }

    private fun arrayNode(index: Int?, value: RespValue.Array): RespReplyNode {
        val items = value.items
            ?: return RespReplyNode(labelFor(index, "(nil)"), expandable = false, children = emptyList())
        val children = items.mapIndexed { childIndex, item -> nodeFor(childIndex, item) }
        return RespReplyNode(labelFor(index, "[array ${items.size}]"), expandable = true, children = children)
    }

    private fun mapNode(index: Int?, value: RespValue.Map): RespReplyNode {
        val children = value.entries.mapIndexed { childIndex, entry -> entryNode(childIndex, entry) }
        return RespReplyNode(labelFor(index, "[map ${value.entries.size}]"), expandable = true, children = children)
    }

    private fun entryNode(index: Int, entry: Pair<RespValue, RespValue>): RespReplyNode {
        val valueNode = nodeFor(index = null, value = entry.second)
        val keyText = scalarText(entry.first)
        return if (valueNode.expandable) {
            RespReplyNode("[$index] $keyText =", expandable = true, children = valueNode.children)
        } else {
            RespReplyNode("[$index] $keyText = ${scalarText(entry.second)}", expandable = false, children = emptyList())
        }
    }

    private fun labelFor(index: Int?, preview: String): String =
        if (index == null) preview else "[$index] $preview"

    private fun scalarText(value: RespValue): String = when (value) {
        is RespValue.Simple -> value.text
        is RespValue.Error -> "(error) ${value.klass} ${value.message}".trim()
        is RespValue.Integer -> value.value.toString()
        is RespValue.Double -> value.value.toString()
        is RespValue.Bool -> value.value.toString()
        is RespValue.Bulk -> bulkPreview(value)
        is RespValue.Array -> if (value.items == null) "(nil)" else "[array ${value.items.size}]"
        is RespValue.Map -> "[map ${value.entries.size}]"
        RespValue.Null -> "(nil)"
    }

    private fun bulkPreview(value: RespValue.Bulk): String {
        val bytes = value.bytes ?: return "(nil)"
        val text = value.asString()
        return if (text != null && isDisplayable(text)) "\"$text\"" else "<binary ${bytes.size} bytes>"
    }

    private fun isDisplayable(text: String): Boolean = text.none { it.code in 0 until 0x09 || it.code == 0x7f }
}
