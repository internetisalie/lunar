package net.internetisalie.lunar.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.customizeLuaInterpreterComboBox
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.project.PlatformLibraryIndex
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel

class LuaProjectSettingsPanel(val project: Project) {
    val mainPanel: JPanel
    private val platformComboBox: ComboBox<LuaPlatform>
    private val versionComboBox: ComboBox<VersionEntry>
    private val languageLevelLabel: JLabel
    private val hererocksManagedCheckBox: JCheckBox
    private val interpreter: ComboBox<LuaInterpreter>
    private val sourcePath: ExpandableTextField
    private val suppressUnderscorePrefixedCheckBox: JCheckBox
    private val rocksServerUrl: JBTextField

    init {
        platformComboBox = ComboBox(PlatformVersionRegistry.platforms().sortedBy { it.label }.toTypedArray())

        versionComboBox = ComboBox()
        versionComboBox.renderer = SimpleListCellRenderer.create { label, value, _ ->
            label.text = value?.label ?: ""
        }

        languageLevelLabel = JLabel()

        hererocksManagedCheckBox = JCheckBox("Hererocks managed (active environment drives interpreter, platform & version)")
        hererocksManagedCheckBox.toolTipText =
            "When enabled, binding or switching a hererocks environment sets the project interpreter, " +
            "platform, and version. Disable to keep an explicitly chosen interpreter across bind/unbind."

        interpreter = ComboBox()
        customizeLuaInterpreterComboBox(project, interpreter)

        sourcePath = ExpandableTextField(
            { value -> value.split(PathConfiguration.TEMPLATE_SEPARATOR) },
            { entries -> entries.joinToString(PathConfiguration.TEMPLATE_SEPARATOR) },
        )
        sourcePath.columns = 60
        sourcePath.text = PathConfiguration.DEFAULT_SOURCE_PATH

        suppressUnderscorePrefixedCheckBox = JCheckBox(
            "Suppress symbols with underscore prefix (_) from completion suggestions"
        )

        rocksServerUrl = JBTextField()
        rocksServerUrl.columns = 60
        rocksServerUrl.emptyText.text = "Empty = use app default or luarocks.org"

        platformComboBox.addItemListener {
            onPlatformChanged(platformComboBox.selectedItem as? LuaPlatform)
        }
        versionComboBox.addItemListener {
            updateLanguageLevelDisplay()
        }
        hererocksManagedCheckBox.addItemListener {
            updateManagedEnablement()
        }

        // Seed version combo for the initially selected platform
        onPlatformChanged(platformComboBox.selectedItem as? LuaPlatform)

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Platform", platformComboBox, 0)
            .addLabeledComponent("Version", versionComboBox, 2)
            .addLabeledComponent("Language Level", languageLevelLabel, 2)
            .addComponent(hererocksManagedCheckBox, 2)
            .addLabeledComponent("Interpreter", interpreter, 2)
            .addLabeledComponent("Source path patterns", sourcePath, 2)
            .addComponent(suppressUnderscorePrefixedCheckBox, 2)
            .addLabeledComponent("LuaRocks server URL (project override)", rocksServerUrl, 2)
            .addComponentFillVertically(JPanel(), 2)
            .panel
    }

    private fun onPlatformChanged(platform: LuaPlatform?) {
        if (platform == null) return
        val versions = PlatformVersionRegistry.getVersions(platform)
        versionComboBox.removeAllItems()
        versions.forEach { versionComboBox.addItem(it) }
        if (versionComboBox.itemCount > 0) {
            versionComboBox.selectedIndex = versionComboBox.itemCount - 1
        }
        updateLanguageLevelDisplay()
    }

    private fun updateLanguageLevelDisplay() {
        val platform = platformComboBox.selectedItem as? LuaPlatform ?: return
        val version = versionComboBox.selectedItem as? VersionEntry ?: return
        val level = Target(platform, version).getImplicitLanguageLevel()
        languageLevelLabel.text = level.toString()
    }

    /**
     * In Hererocks-managed mode the active env owns the interpreter/platform/version, so those
     * controls become read-only derived views (mirroring the always-derived Language Level label).
     */
    private fun updateManagedEnablement() {
        val managed = hererocksManagedCheckBox.isSelected
        platformComboBox.isEnabled = !managed
        versionComboBox.isEnabled = !managed
        interpreter.isEnabled = !managed
    }

    fun apply(state: LuaProjectSettings.State) {
        val settings = LuaProjectSettings.getInstance(project)
        // Mode-independent fields always apply.
        state.sourcePath = sourcePath.text
        state.suppressUnderscorePrefixedGlobals = suppressUnderscorePrefixedCheckBox.isSelected
        state.rocksServerUrl = rocksServerUrl.text.trim()

        val newMode = if (hererocksManagedCheckBox.isSelected) InterpreterMode.HEREROCKS_MANAGED
                      else InterpreterMode.EXPLICIT
        when {
            // Explicit throughout: the combos own the interpreter/target.
            newMode == InterpreterMode.EXPLICIT && state.interpreterMode == InterpreterMode.EXPLICIT ->
                applyExplicitSelections(state)
            // Any mode transition is delegated to the settings layer, which stashes/restores the
            // explicit overlay and (for Managed) re-derives from the active env off the EDT. The
            // combos are ignored on transition — Managed derives them; leaving Managed restores them.
            newMode != state.interpreterMode ->
                settings.setInterpreterModeAndNotify(project, newMode)
            // Managed → Managed: the env owns interpreter/target; nothing to apply from the panel.
        }
    }

    private fun applyExplicitSelections(state: LuaProjectSettings.State) {
        val platform = platformComboBox.selectedItem as? LuaPlatform ?: return
        val version = versionComboBox.selectedItem as? VersionEntry ?: return
        val newTarget = Target(platform, version)
        val previousTarget = state.getTarget()
        state.setTarget(newTarget)
        state.interpreter = interpreter.selectedItem as? LuaInterpreter
        if (newTarget.getImplicitLanguageLevel() != previousTarget.getImplicitLanguageLevel()) {
            PlatformLibraryIndex.reload()
        }
    }

    fun reset() {
        setData(LuaProjectSettings.getInstance(project).state)
    }

    fun setData(data: LuaProjectSettings.State) {
        val target = data.getTarget()
        platformComboBox.selectedItem = target.platform
        onPlatformChanged(target.platform)
        versionComboBox.selectedItem = target.version
        updateLanguageLevelDisplay()
        interpreter.selectedItem = data.interpreter
        sourcePath.text = data.sourcePath
        suppressUnderscorePrefixedCheckBox.isSelected = data.suppressUnderscorePrefixedGlobals
        rocksServerUrl.text = data.rocksServerUrl

        val managed = data.interpreterMode == InterpreterMode.HEREROCKS_MANAGED
        hererocksManagedCheckBox.isSelected = managed
        // Managed mode is only meaningful with an env to manage; keep it toggleable once on so the
        // user can always switch back to Explicit.
        val hasActiveEnv = LuaProjectSettings.getInstance(project).activeEnv() != null
        hererocksManagedCheckBox.isEnabled = managed || hasActiveEnv
        updateManagedEnablement()
    }

    fun isModified(data: LuaProjectSettings.State): Boolean {
        val managedNow = hererocksManagedCheckBox.isSelected
        if (managedNow != (data.interpreterMode == InterpreterMode.HEREROCKS_MANAGED)) return true
        if (sourcePath.text != data.sourcePath) return true
        if (suppressUnderscorePrefixedCheckBox.isSelected != data.suppressUnderscorePrefixedGlobals) return true
        if (rocksServerUrl.text.trim() != data.rocksServerUrl) return true
        // Interpreter/platform/version are env-derived (and read-only) in Managed mode; only compare
        // them when the user is in explicit control.
        if (!managedNow) {
            val savedTarget = data.getTarget()
            val currentPlatform = platformComboBox.selectedItem as? LuaPlatform ?: return false
            val currentVersion = versionComboBox.selectedItem as? VersionEntry ?: return false
            if (currentPlatform != savedTarget.platform) return true
            if (currentVersion != savedTarget.version) return true
            if (interpreter.selectedItem != data.interpreter) return true
        }
        return false
    }
}