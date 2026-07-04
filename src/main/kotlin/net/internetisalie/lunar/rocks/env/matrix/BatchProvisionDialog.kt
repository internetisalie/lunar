package net.internetisalie.lunar.rocks.env.matrix

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.rocks.env.HererocksFlavor
import javax.swing.JComponent

/**
 * Collects a base directory + a comma-separated set of Lua versions to provision as a PUC matrix
 * (ROCKS-15-05, design §2.7). Kept intentionally simple: LuaJIT rows can be added via the ROCKS-14
 * create dialog; the batch flow targets the common PUC-version matrix.
 */
class BatchProvisionDialog(private val targetProject: Project) : DialogWrapper(targetProject) {

    private val baseDirField = TextFieldWithBrowseButton()
    private val versionsField = JBTextField("5.1, 5.2, 5.3, 5.4")

    init {
        title = "Provision Version Matrix"
        baseDirField.text = "${targetProject.guessProjectDir()?.path ?: ""}/.lua-matrix"
        baseDirField.addBrowseFolderListener(
            targetProject,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Matrix Base Directory")
                .withDescription("Directory under which one env is provisioned per version"),
        )
        init()
    }

    override fun createCenterPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent("Base directory:", baseDirField)
            .addLabeledComponent("Lua versions (comma-separated):", versionsField)
            .panel

    override fun doValidate(): ValidationInfo? {
        if (baseDirField.text.isBlank()) return ValidationInfo("Base directory is required", baseDirField)
        if (parseVersions().isEmpty()) return ValidationInfo("At least one Lua version is required", versionsField)
        return null
    }

    fun baseDir(): String = baseDirField.text.trim()

    fun rows(): List<BatchRow> = parseVersions().map { BatchRow(HererocksFlavor.PUC, it) }

    private fun parseVersions(): List<String> =
        versionsField.text.split(',').map { it.trim() }.filter { it.isNotBlank() }
}
