package net.internetisalie.lunar.rocks.init

import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings UI peer for the LuaRocks project generator.
 *
 * Provides a simple panel with name, project kind (Single Rock / Workspace),
 * project type (Library / Application), and optional feature checkboxes.
 * The panel is kept minimal for headless-safe testing; complex show/hide
 * interactions are handled via enabled-state on the checkboxes.
 */
class LuaRocksGeneratorPeer : ProjectGeneratorPeer<LuaRocksProjectSettings> {

    // --- widgets -----------------------------------------------------------

    private val nameField = JBTextField()

    private val singleRockButton = JBRadioButton("Single Rock", true)
    private val workspaceButton = JBRadioButton("Workspace", false)

    private val libraryButton = JBRadioButton("Library", true)
    private val applicationButton = JBRadioButton("Application", false)

    private val loaderSetupCheck = JBCheckBox("Loader Setup (src/setup.lua)")
    private val bustedConfigCheck = JBCheckBox("Busted Configuration (spec/)")
    private val makefileCheck = JBCheckBox("Makefile")

    private val workspaceNameField = JBTextField()
    private val initialRocksField = JBTextField()

    private val panel: JPanel

    init {
        val kindGroup = ButtonGroup().also {
            it.add(singleRockButton)
            it.add(workspaceButton)
        }
        val typeGroup = ButtonGroup().also {
            it.add(libraryButton)
            it.add(applicationButton)
        }

        // Loader Setup is only meaningful for Application
        applicationButton.addChangeListener {
            loaderSetupCheck.isEnabled = applicationButton.isSelected
            if (!applicationButton.isSelected) loaderSetupCheck.isSelected = false
        }
        loaderSetupCheck.isEnabled = false

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Project name:", nameField)
            .addSeparator()
            .addComponent(JBLabel("Project kind:"))
            .addComponent(singleRockButton)
            .addComponent(workspaceButton)
            .addSeparator()
            .addComponent(JBLabel("Project type (Single Rock):"))
            .addComponent(libraryButton)
            .addComponent(applicationButton)
            .addSeparator()
            .addComponent(JBLabel("Options (Single Rock):"))
            .addComponent(loaderSetupCheck)
            .addComponent(bustedConfigCheck)
            .addComponent(makefileCheck)
            .addSeparator()
            .addLabeledComponent("Workspace name:", workspaceNameField)
            .addLabeledComponent("Initial rocks (comma-separated):", initialRocksField)
            .panel
    }

    // --- ProjectGeneratorPeer ----------------------------------------------

    override fun getComponent(
        locationField: TextFieldWithBrowseButton,
        checkValid: Runnable,
    ): JComponent = panel

    override fun buildUI(settingsStep: SettingsStep) {
        settingsStep.addSettingsComponent(panel)
    }

    override fun getSettings(): LuaRocksProjectSettings {
        val name = nameField.text.trim()
        val kind = if (workspaceButton.isSelected) RockKind.WORKSPACE else RockKind.SINGLE_ROCK
        val type = if (applicationButton.isSelected) RockType.APPLICATION else RockType.LIBRARY
        val rocksText = initialRocksField.text.trim()
        val initialRocks = if (rocksText.isBlank()) emptyList()
        else rocksText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return LuaRocksProjectSettings(
            name = name,
            kind = kind,
            type = type,
            loaderSetup = loaderSetupCheck.isSelected,
            bustedConfig = bustedConfigCheck.isSelected,
            makefile = makefileCheck.isSelected,
            workspaceName = workspaceNameField.text.trim(),
            initialRocks = initialRocks,
        )
    }

    override fun validate(): ValidationInfo? {
        val name = nameField.text.trim()
        if (name.isBlank()) return ValidationInfo("Project name must not be empty", nameField)
        if (!name.matches(Regex("[A-Za-z0-9._-]+"))) {
            return ValidationInfo(
                "Project name must contain only letters, digits, '.', '_', or '-'",
                nameField,
            )
        }
        return null
    }

    override fun isBackgroundJobRunning(): Boolean = false
}
