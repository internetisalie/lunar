package net.internetisalie.lunar.rocks.init

import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaInterpreterListCellRenderer
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.rocks.env.HererocksFlavor
import net.internetisalie.lunar.settings.LuaApplicationSettings
import javax.swing.ButtonGroup
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings UI peer for the LuaRocks project generator.
 *
 * Collects name, project type (Library / Application), the target Lua flavor + version, an
 * interpreter choice (hererocks-provisioned or an existing registered interpreter), and optional
 * feature checkboxes. Kept construction-only for headless-safe testing; show/hide interactions are
 * expressed as enabled-state, not layout swaps.
 */
class LuaRocksGeneratorPeer : ProjectGeneratorPeer<LuaRocksProjectSettings> {

    // --- widgets -----------------------------------------------------------

    private val nameField = JBTextField()

    private val libraryButton = JBRadioButton("Library", true)
    private val applicationButton = JBRadioButton("Application", false)

    // `internal` (not private) so same-module tests can drive the flavor/version/provision logic.
    internal val flavorCombo = ComboBox(HererocksFlavor.entries.toTypedArray())
    internal val versionCombo = ComboBox<VersionEntry>().apply {
        renderer = SimpleListCellRenderer.create { label, value, _ -> label.text = value?.label ?: "" }
    }
    internal val provisionCheck = JBCheckBox("Provision isolated environment with hererocks")
    internal val interpreterCombo = ComboBox<LuaInterpreter>().apply {
        model = DefaultComboBoxModel(LuaApplicationSettings.validInterpreters().toTypedArray())
        renderer = LuaInterpreterListCellRenderer()
    }

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

        // Version list is flavor-dependent (PUC → 5.x incl. 5.5, LuaJIT → 2.x), sourced from the
        // authoritative registry so it never drifts from the supported targets.
        flavorCombo.addActionListener { repopulateVersions() }
        repopulateVersions()

        // The existing-interpreter combo is only relevant when NOT provisioning an isolated env.
        provisionCheck.addActionListener { updateInterpreterEnablement() }
        updateInterpreterEnablement()

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Project name:", nameField)
            .addSeparator()
            .addComponent(JBLabel("Project type:"))
            .addComponent(libraryButton)
            .addComponent(applicationButton)
            .addSeparator()
            .addComponent(JBLabel("Interpreter:"))
            .addLabeledComponent("Flavor:", flavorCombo)
            .addLabeledComponent("Lua version:", versionCombo)
            .addComponent(provisionCheck)
            .addLabeledComponent("Existing interpreter:", interpreterCombo)
            .addSeparator()
            .addComponent(JBLabel("Options:"))
            .addComponent(loaderSetupCheck)
            .addComponent(bustedConfigCheck)
            .addComponent(makefileCheck)
            .panel
    }

    private fun platformFor(flavor: HererocksFlavor): LuaPlatform =
        if (flavor == HererocksFlavor.LUAJIT) LuaPlatform.LUAJIT else LuaPlatform.STANDARD

    private fun repopulateVersions() {
        val flavor = flavorCombo.selectedItem as? HererocksFlavor ?: HererocksFlavor.PUC
        val versions = PlatformVersionRegistry.getVersions(platformFor(flavor))
        versionCombo.model = DefaultComboBoxModel(versions.toTypedArray())
        // Prefer the conventional default (PUC 5.4) when present, else the newest entry.
        val preferred = versions.firstOrNull { it.label == DEFAULT_PUC_VERSION } ?: versions.lastOrNull()
        versionCombo.selectedItem = preferred
    }

    private fun updateInterpreterEnablement() {
        interpreterCombo.isEnabled = !provisionCheck.isSelected
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
            flavor = flavorCombo.selectedItem as? HererocksFlavor ?: HererocksFlavor.PUC,
            luaVersion = (versionCombo.selectedItem as? VersionEntry)?.label ?: DEFAULT_PUC_VERSION,
            provisionHererocks = provisionCheck.isSelected,
            interpreterPath = (interpreterCombo.selectedItem as? LuaInterpreter)?.path.orEmpty(),
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

    companion object {
        private const val DEFAULT_PUC_VERSION = "5.4"
    }
}
