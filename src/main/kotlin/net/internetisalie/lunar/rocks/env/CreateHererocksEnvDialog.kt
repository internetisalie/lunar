package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import javax.swing.DefaultComboBoxModel
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
    // Editable so a custom version / git ref can still be typed; the option list is gated by flavor.
    private val luaVersionCombo = JComboBox<String>().apply { isEditable = true }
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
        // The Lua-version options follow the selected flavor (PUC → 5.x, LuaJIT → 2.x).
        flavorCombo.addActionListener { repopulateVersions() }
        repopulateVersions()
        initial?.let {
            flavorCombo.selectedItem = it.flavor
            repopulateVersions()
            luaVersionCombo.selectedItem = it.luaVersion
            luarocksVersionField.text = it.luarocksVersion
        }
        init()
    }

    private fun repopulateVersions() {
        val flavor = flavorCombo.selectedItem as? HererocksFlavor ?: HererocksFlavor.PUC
        val platform = if (flavor == HererocksFlavor.LUAJIT) LuaPlatform.LUAJIT else LuaPlatform.STANDARD
        val versions = PlatformVersionRegistry.getVersions(platform).map { it.label }
        luaVersionCombo.model = DefaultComboBoxModel(versions.toTypedArray())
        luaVersionCombo.selectedItem = versions.firstOrNull { it == "5.4" } ?: versions.lastOrNull()
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
