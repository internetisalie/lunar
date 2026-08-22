package net.internetisalie.lunar.rocks.ui

import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import net.internetisalie.lunar.rocks.deps.DependencyNode
import java.awt.CardLayout
import javax.swing.JEditorPane
import javax.swing.JPanel

/**
 * Shows detail for the selected dependency node: resolved version, the constraints requiring it,
 * its reverse dependencies ("required by"), and any conflicts. Read-only HTML.
 *
 * A [CardLayout] toggles between the empty state and the detail body. The empty state is a
 * [JBPanelWithEmptyText] — centred, dimmed and non-italic like every platform empty state — rather
 * than the italic HTML string in a label it used to be (BUG-448 #14). Platform empty text takes no
 * trailing period.
 */
class DependencyInspectorPanel : JPanel(CardLayout()) {
    private val cards = layout as CardLayout
    private val content =
        JEditorPane("text/html", "").apply {
            isEditable = false
            border = JBUI.Borders.empty(8)
        }
    private val emptyCard = JBPanelWithEmptyText().withEmptyText(EMPTY_TEXT)

    init {
        add(emptyCard, EMPTY_CARD)
        add(JBScrollPane(content), DETAIL_CARD)
        show(null)
    }

    fun show(node: DependencyNode?) {
        if (node == null) {
            cards.show(this, EMPTY_CARD)
            return
        }
        content.text = render(node)
        content.caretPosition = 0
        cards.show(this, DETAIL_CARD)
    }

    private fun render(node: DependencyNode): String =
        buildString {
            append("<html><body style='font-family:sans-serif'>")
            append("<h3>").append(escape(node.packageName)).append("</h3>")
            append("<p><b>Version:</b> ")
                .append(
                    node.resolvedVersion?.raw?.let {
                        escape(it)
                    } ?: "<i>not installed</i>",
                ).append("</p>")
            append("<p><b>Scope:</b> ").append(if (node.isTransitive) "transitive" else "direct").append("</p>")
            appendList("Constraints", node.requiredConstraints.map { "${it.op.token} ${it.version.raw}" })
            appendList("Required by", node.requiredBy.map { it.packageName })
            if (node.conflicts.isNotEmpty()) {
                append("<h4 style='color:#c0392b'>Conflicts</h4><ul>")
                node.conflicts.forEach { append("<li>").append(escape(it.description)).append("</li>") }
                append("</ul>")
            }
            append("</body></html>")
        }

    private fun StringBuilder.appendList(
        title: String,
        items: List<String>,
    ) {
        if (items.isEmpty()) return
        append("<p><b>").append(title).append(":</b></p><ul>")
        items.forEach { append("<li>").append(escape(it)).append("</li>") }
        append("</ul>")
    }

    private fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private companion object {
        const val EMPTY_CARD = "empty"
        const val DETAIL_CARD = "detail"
        const val EMPTY_TEXT = "Select a dependency"
    }
}
