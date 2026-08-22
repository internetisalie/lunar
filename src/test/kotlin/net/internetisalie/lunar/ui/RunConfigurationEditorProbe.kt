package net.internetisalie.lunar.ui

import com.intellij.openapi.options.SettingsEditor
import java.awt.Component
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Reads a built run-configuration editor back the way a user sees it.
 *
 * A `SettingsEditor`'s panel can be constructed headless, so the label text, the mnemonic model and
 * a combo's *rendered* item text are all observable without a window — which is what makes the
 * BUG-448 group-5 findings testable at all. What is not observable here is anything that only
 * exists once painted: a mnemonic underline, or a rendered column width.
 */
internal object RunConfigurationEditorProbe {
    /**
     * The leading labels of a `FormBuilder` form. `FormBuilder` parents every row's label directly
     * on the form panel and sets `labelFor` on it, while the labels `addTooltip` adds are row
     * *components* with no `labelFor` — so the property separates the two cleanly.
     */
    fun formLabelsOf(editor: SettingsEditor<*>): List<JLabel> =
        editor.component.components
            .filterIsInstance<JLabel>()
            .filter { it.labelFor != null && it.text.isNotBlank() }

    /** The control a leading label names, found through the `labelFor` the label carries. */
    fun controlLabelled(
        labelText: String,
        editor: SettingsEditor<*>,
    ): Component {
        val label = formLabelsOf(editor).firstOrNull { it.text == labelText }
        requireNotNull(label) { "no row labelled '$labelText'" }
        return requireNotNull(label.labelFor) { "row '$labelText' names no control" }
    }

    fun comboLabelled(
        labelText: String,
        editor: SettingsEditor<*>,
    ): JComboBox<*> = controlLabelled(labelText, editor) as JComboBox<*>

    fun itemsOf(combo: JComboBox<*>): List<Any?> = (0 until combo.itemCount).map { combo.getItemAt(it) }

    /** What [combo]'s renderer displays for [item] — the text a user reads, not the stored value. */
    fun renderedText(
        combo: JComboBox<*>,
        item: Any?,
    ): String {
        @Suppress("UNCHECKED_CAST")
        val renderer = combo.renderer as ListCellRenderer<Any?>
        val rendered = renderer.getListCellRendererComponent(JList<Any?>(), item, 0, false, false)
        return (rendered as? JLabel)?.text.orEmpty()
    }

    fun checkBoxesOf(editor: SettingsEditor<*>): List<JCheckBox> =
        descendantsOf(editor.component).filterIsInstance<JCheckBox>()

    private fun descendantsOf(root: JComponent): List<JComponent> =
        root.components.filterIsInstance<JComponent>().flatMap { listOf(it) + descendantsOf(it) }
}
