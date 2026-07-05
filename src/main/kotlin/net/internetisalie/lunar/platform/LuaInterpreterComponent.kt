package net.internetisalie.lunar.platform

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleTextAttributes
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.settings.LuaApplicationSettings
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.util.newAppBackgroundTask
import javax.swing.DefaultComboBoxModel
import javax.swing.JList
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

fun customizeLuaInterpreterComboBox(project : Project, interpreterField : ComboBox<LuaInterpreter>) {
    // Offer the project interpreter (which may be a project-scoped hererocks-managed env whose path
    // isn't in the global list) as a selectable option, not just the globally-registered ones
    // (ROCKS-16 follow-up) — otherwise a managed interpreter can't be picked in a run/debug config.
    val projectInterpreter = LuaProjectSettings.getInstance(project).state.interpreter

    // Build the combo model as: [typed] + [project interpreter] + [valid globals], de-duplicated by
    // path. Used both for the initial model AND the DocumentListener rebuild below — the rebuild is
    // the subtle bit: when the editor's text is a path not in the global list (e.g. the run config
    // already stores a non-registered interpreter), the listener rebuilds the model, and it MUST keep
    // the project interpreter or a managed env would silently disappear from the dropdown.
    fun buildModel(typed: LuaInterpreter? = null): DefaultComboBoxModel<LuaInterpreter> {
        val byPath = LinkedHashMap<String, LuaInterpreter>()
        fun add(interpreter: LuaInterpreter?) {
            val path = interpreter?.path ?: return
            byPath.putIfAbsent(path, interpreter)
        }
        add(typed)
        add(projectInterpreter)
        LuaApplicationSettings.validInterpreters().forEach(::add)
        return DefaultComboBoxModel(byPath.values.toTypedArray())
    }

    interpreterField.model = buildModel()
    interpreterField.item = projectInterpreter
    interpreterField.renderer = LuaInterpreterListCellRenderer()
    interpreterField.isEditable = true
    val component = interpreterField.editor.editorComponent
    if (component is JTextField) component.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            if (component.text.isEmpty()) {
                interpreterField.selectedItem = null
                return
            }

            // Match against the globals AND the project interpreter — selecting the managed env by
            // path must not fall through to the rebuild-with-Unknown branch.
            val existingInterpreter = LuaApplicationSettings.validInterpreters().firstOrNull { it.path == component.text }
                ?: projectInterpreter?.takeIf { it.path == component.text }
            if (existingInterpreter != null) {
                interpreterField.selectedItem = existingInterpreter
                return
            }

            val interpreter = LuaInterpreter(
                path = component.text,
                product = LuaInterpreterFamily.UNKNOWN_PRODUCT,
            )
            interpreterField.model = buildModel(interpreter)
            interpreterField.selectedItem = interpreter

            newAppBackgroundTask(LuaBundle.message("action.inspect.interpreter")) {
                LuaInterpreterService.getInstance().identify(interpreter)
            }.queue()
        }
    })
}

class LuaInterpreterListCellRenderer() : ColoredListCellRenderer<Any>() {
    override fun customizeCellRenderer(
        list: JList<out Any?>,
        value: Any?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        when (value) {
            null -> {
                append("No interpreter selected", SimpleTextAttributes.ERROR_ATTRIBUTES)
            }

            is LuaInterpreter -> {
                val family = value.familyOrUnknown
                append("${value.path}", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                if (!value.valid) {
                    append(" Invalid", SimpleTextAttributes.ERROR_ATTRIBUTES)
                    icon = AllIcons.General.Error
                } else if (value.product == LuaInterpreterFamily.UNKNOWN_PRODUCT) {
                    append(" Unknown", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                    icon = AllIcons.Nodes.Unknown
                } else {
                    append(" ${value.product} ${value.version}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                    icon = family.icon
                }
            }

            else -> {
                append("Unknown Interpreter ", SimpleTextAttributes.ERROR_ATTRIBUTES)
                append(value.toString(), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            }
        }
    }
}