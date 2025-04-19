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
    interpreterField.model = DefaultComboBoxModel(
        LuaApplicationSettings.validInterpreters().toTypedArray()
    )
    interpreterField.item = LuaProjectSettings.getInstance(project).state.interpreter
    interpreterField.renderer = LuaInterpreterListCellRenderer()
    interpreterField.isEditable = true
    val component = interpreterField.editor.editorComponent
    if (component is JTextField) component.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            if (component.text.isEmpty()) {
                interpreterField.selectedItem = null
                return
            }

            val validInterpreters = LuaApplicationSettings.validInterpreters()

            val existingInterpreter = validInterpreters.firstOrNull { it.path == component.text }
            if (existingInterpreter != null) {
                interpreterField.selectedItem = existingInterpreter
                return
            }

            val interpreter = LuaInterpreter(
                path = component.text,
                product = LuaInterpreterFamily.UNKNOWN_PRODUCT,
            )
            interpreterField.model = DefaultComboBoxModel(
                listOf(listOf(interpreter), validInterpreters)
                    .flatten()
                    .toTypedArray()
            )
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