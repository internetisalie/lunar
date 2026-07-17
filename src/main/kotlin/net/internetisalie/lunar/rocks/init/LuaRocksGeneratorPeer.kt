package net.internetisalie.lunar.rocks.init

import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.ui.LuaRuntimeComboBox
import javax.swing.ButtonGroup
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings UI peer for the LuaRocks project generator.
 *
 * Collects name, project type (Library / Application), the target runtime kind + version, an
 * interpreter choice (provisioned or an existing registered runtime tool), and optional feature
 * checkboxes. Kept construction-only for headless-safe testing; show/hide interactions are expressed
 * as enabled-state, not layout swaps.
 */
class LuaRocksGeneratorPeer : ProjectGeneratorPeer<LuaRocksProjectSettings> {

    // --- widgets -----------------------------------------------------------

    private val nameField = JBTextField()

    private val libraryButton = JBRadioButton("Library", true)
    private val applicationButton = JBRadioButton("Application", false)

    // `internal` (not private) so same-module tests can drive the kind/version/provision logic.
    internal val kindCombo = ComboBox(arrayOf(WizardRuntimeKinds.LUA, WizardRuntimeKinds.LUAJIT)).apply {
        renderer = SimpleListCellRenderer.create { label, value, _ ->
            label.text = if (value == WizardRuntimeKinds.LUAJIT) "LuaJIT" else "Lua"
        }
    }
    internal val versionCombo = ComboBox<VersionEntry>().apply {
        renderer = SimpleListCellRenderer.create { label, value, _ -> label.text = value?.label ?: "" }
    }
    internal val provisionCheck = JBCheckBox("Provision isolated environment")
    internal val interpreterCombo = ComboBox<LuaRegisteredTool>().apply {
        LuaRuntimeComboBox.customize(ProjectManager.getInstance().defaultProject, this)
    }

    private val loaderSetupCheck = JBCheckBox("Loader Setup (src/setup.lua)")
    private val bustedConfigCheck = JBCheckBox("Busted Configuration (spec/)")
    private val makefileCheck = JBCheckBox("Makefile")

    // Built lazily on first component access (always on the EDT in the wizard) so the peer's widget
    // state is still construction-time testable off the EDT — the Kotlin UI DSL requires the EDT.
    private val panel: JPanel by lazy {
        panel {
            row("Project name:") { cell(nameField) }
            group("Project type") {
                row { cell(libraryButton) }
                row { cell(applicationButton) }
            }
            group("Interpreter") {
                row("Runtime:") { cell(kindCombo) }
                row("Lua version:") { cell(versionCombo) }
                row { cell(provisionCheck) }
                row("Existing interpreter:") { cell(interpreterCombo) }
            }
            group("Options") {
                row { cell(loaderSetupCheck) }
                row { cell(bustedConfigCheck) }
                row { cell(makefileCheck) }
            }
        }
    }

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

        // Version list is kind-dependent (Lua → 5.x incl. 5.5, LuaJIT → 2.x), sourced from the
        // authoritative registry so it never drifts from the supported targets.
        kindCombo.addActionListener { repopulateVersions() }
        repopulateVersions()

        // The existing-interpreter combo is only relevant when NOT provisioning an isolated env.
        provisionCheck.addActionListener { updateInterpreterEnablement() }
        updateInterpreterEnablement()
    }

    private fun platformFor(kindId: String): LuaPlatform =
        if (kindId == WizardRuntimeKinds.LUAJIT) LuaPlatform.LUAJIT else LuaPlatform.STANDARD

    private fun repopulateVersions() {
        val kindId = kindCombo.selectedItem as? String ?: WizardRuntimeKinds.LUA
        val versions = PlatformVersionRegistry.getVersions(platformFor(kindId))
        versionCombo.model = DefaultComboBoxModel(versions.toTypedArray())
        // Prefer the conventional default (Lua 5.4) when present, else the newest entry.
        val preferred = versions.firstOrNull { it.label == DEFAULT_LUA_VERSION } ?: versions.lastOrNull()
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
            kindId = kindCombo.selectedItem as? String ?: WizardRuntimeKinds.LUA,
            luaVersion = (versionCombo.selectedItem as? VersionEntry)?.label ?: DEFAULT_LUA_VERSION,
            provisionEnvironment = provisionCheck.isSelected,
            interpreterPath = (interpreterCombo.selectedItem as? LuaRegisteredTool)?.path.orEmpty(),
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
        private const val DEFAULT_LUA_VERSION = "5.4"
    }
}
