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
 * Provides a simple panel with name, project type (Library / Application), and optional feature
 * checkboxes. The panel is kept minimal for headless-safe testing; complex show/hide interactions
 * are handled via enabled-state on the checkboxes.
 */
class LuaRocksGeneratorPeer : ProjectGeneratorPeer<LuaRocksProjectSettings> {

    // --- widgets -----------------------------------------------------------

    private val nameField = JBTextField()

    private val libraryButton = JBRadioButton("Library", true)
    private val applicationButton = JBRadioButton("Application", false)

    private val loaderSetupCheck = JBCheckBox("Loader Setup (src/setup.lua)")
    private val bustedConfigCheck = JBCheckBox("Busted Configuration (spec/)")
    private val makefileCheck = JBCheckBox("Makefile")

    private val panel: JPanel

    init {
        ButtonGroup().also {
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
            .addComponent(JBLabel("Project type:"))
            .addComponent(libraryButton)
            .addComponent(applicationButton)
            .addSeparator()
            .addComponent(JBLabel("Options:"))
            .addComponent(loaderSetupCheck)
            .addComponent(bustedConfigCheck)
            .addComponent(makefileCheck)
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
        val type = if (applicationButton.isSelected) RockType.APPLICATION else RockType.LIBRARY
        return LuaRocksProjectSettings(
            name = name,
            type = type,
            loaderSetup = loaderSetupCheck.isSelected,
            bustedConfig = bustedConfigCheck.isSelected,
            makefile = makefileCheck.isSelected,
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
