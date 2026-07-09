package net.internetisalie.lunar.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.project.PlatformLibraryIndex
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Project-level Lua settings page (interim slimming, TOOLING-05 §6.3). The interpreter combo and the
 * env-managed-mode checkbox were removed — runtime selection is now a toolchain RUNTIME binding
 * / active environment (TOOLING-02) surfaced by the Toolchain page (TOOLING-06). Platform, version,
 * language level, source path, and the LuaRocks server override remain here until TOOLING-06 folds
 * this page.
 */
class LuaProjectSettingsPanel(val project: Project) {
    val mainPanel: JPanel
    private val platformComboBox: ComboBox<LuaPlatform>
    private val versionComboBox: ComboBox<VersionEntry>
    private val languageLevelLabel: JLabel
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

        sourcePath = ExpandableTextField(
            { value -> value.split(PathConfiguration.TEMPLATE_SEPARATOR) },
            { entries -> entries.joinToString(PathConfiguration.TEMPLATE_SEPARATOR) },
        )
        sourcePath.columns = 60
        sourcePath.text = PathConfiguration.DEFAULT_SOURCE_PATH

        suppressUnderscorePrefixedCheckBox = JCheckBox(
            "Hide symbols with an underscore prefix (_) from suggestions"
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

        // Seed version combo for the initially selected platform
        onPlatformChanged(platformComboBox.selectedItem as? LuaPlatform)

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(TitledSeparator("Interpreter & Target"))
            .addLabeledComponent("Platform", platformComboBox, 0)
            .addLabeledComponent("Version", versionComboBox, 2)
            .addLabeledComponent("Language level", languageLevelLabel, 2)
            .addComponent(TitledSeparator("Source & Completion"))
            .addLabeledComponent("Source path patterns", sourcePath, 2)
            .addLabeledComponent("Completion", suppressUnderscorePrefixedCheckBox, 2)
            .addComponent(TitledSeparator("LuaRocks"))
            .addLabeledComponent("Server URL (project override)", rocksServerUrl, 2)
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

    fun apply(state: LuaProjectSettings.State) {
        state.sourcePath = sourcePath.text
        state.suppressUnderscorePrefixedGlobals = suppressUnderscorePrefixedCheckBox.isSelected
        state.rocksServerUrl = rocksServerUrl.text.trim()
        applyTargetSelection(state)
    }

    private fun applyTargetSelection(state: LuaProjectSettings.State) {
        val platform = platformComboBox.selectedItem as? LuaPlatform ?: return
        val version = versionComboBox.selectedItem as? VersionEntry ?: return
        val newTarget = Target(platform, version)
        val previousTarget = state.getTarget()
        state.setTarget(newTarget)
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
        sourcePath.text = data.sourcePath
        suppressUnderscorePrefixedCheckBox.isSelected = data.suppressUnderscorePrefixedGlobals
        rocksServerUrl.text = data.rocksServerUrl
    }

    fun isModified(data: LuaProjectSettings.State): Boolean {
        if (sourcePath.text != data.sourcePath) return true
        if (suppressUnderscorePrefixedCheckBox.isSelected != data.suppressUnderscorePrefixedGlobals) return true
        if (rocksServerUrl.text.trim() != data.rocksServerUrl) return true
        val savedTarget = data.getTarget()
        val currentPlatform = platformComboBox.selectedItem as? LuaPlatform ?: return false
        val currentVersion = versionComboBox.selectedItem as? VersionEntry ?: return false
        if (currentPlatform != savedTarget.platform) return true
        if (currentVersion != savedTarget.version) return true
        return false
    }
}
