package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JComboBox

/**
 * Collects a [HererocksEnvState] spec for create/upgrade (ROCKS-14-03/06, design §2.7). Pre-filled
 * from [initial] when re-provisioning; defaults to `<projectBase>/.lua` + PUC 5.4 otherwise.
 */
class CreateHererocksEnvDialog(
    private val targetProject: Project,
    private val initial: HererocksEnvState?,
) : DialogWrapper(targetProject) {

    private val directoryField = TextFieldWithBrowseButton()
    private val flavorCombo = JComboBox(HererocksFlavor.entries.toTypedArray())
    private val luaVersionCombo = JComboBox(arrayOf("5.1", "5.2", "5.3", "5.4", "5.5", "2.1")).apply { isEditable = true }
    private val luarocksVersionField = JBTextField("latest")

    init {
        title = "Isolated Lua Environment"
        directoryField.text = initial?.directory
            ?: "${targetProject.guessProjectDir()?.path ?: ""}/.lua"
        directoryField.addBrowseFolderListener(
            targetProject,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Environment Directory")
                .withDescription("Select the hererocks environment directory"),
        )
        initial?.let {
            flavorCombo.selectedItem = it.flavor
            luaVersionCombo.selectedItem = it.luaVersion
            luarocksVersionField.text = it.luarocksVersion
        }
        init()
    }

    override fun createCenterPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent("Directory:", directoryField)
            .addLabeledComponent("Flavor:", flavorCombo)
            .addLabeledComponent("Lua version:", luaVersionCombo)
            .addLabeledComponent("LuaRocks version:", luarocksVersionField)
            .panel

    override fun doValidate(): ValidationInfo? {
        if (directoryField.text.isBlank()) return ValidationInfo("Directory is required", directoryField)
        if ((luaVersionCombo.selectedItem as? String).isNullOrBlank()) {
            return ValidationInfo("Lua version is required", luaVersionCombo)
        }
        return null
    }

    fun toSpec(): HererocksEnvState = HererocksEnvState(
        id = initial?.id ?: "",
        directory = directoryField.text.trim(),
        flavor = flavorCombo.selectedItem as HererocksFlavor,
        luaVersion = (luaVersionCombo.selectedItem as? String)?.trim().orEmpty(),
        luarocksVersion = luarocksVersionField.text.trim().ifBlank { "latest" },
    )
}
