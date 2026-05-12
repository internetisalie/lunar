package net.internetisalie.lunar.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
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
import javax.swing.JLabel
import javax.swing.JPanel

class LuaProjectSettingsPanel(val project: Project) {
    val mainPanel: JPanel
    private val platformComboBox: ComboBox<LuaPlatform>
    private val versionComboBox: ComboBox<VersionEntry>
    private val languageLevelLabel: JLabel
    private val interpreter: ComboBox<LuaInterpreter>
    private val sourcePath: ExpandableTextField

    init {
        platformComboBox = ComboBox(PlatformVersionRegistry.platforms().sortedBy { it.label }.toTypedArray())

        versionComboBox = ComboBox()
        versionComboBox.renderer = SimpleListCellRenderer.create { label, value, _ ->
            label.text = value?.label ?: ""
        }

        languageLevelLabel = JLabel()

        interpreter = ComboBox()
        customizeLuaInterpreterComboBox(project, interpreter)

        sourcePath = ExpandableTextField(
            { value -> value.split(PathConfiguration.TEMPLATE_SEPARATOR) },
            { entries -> entries.joinToString(PathConfiguration.TEMPLATE_SEPARATOR) },
        )
        sourcePath.columns = 60
        sourcePath.text = PathConfiguration.DEFAULT_SOURCE_PATH

        platformComboBox.addItemListener {
            onPlatformChanged(platformComboBox.selectedItem as? LuaPlatform)
        }
        versionComboBox.addItemListener {
            updateLanguageLevelDisplay()
        }

        // Seed version combo for the initially selected platform
        onPlatformChanged(platformComboBox.selectedItem as? LuaPlatform)

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Platform", platformComboBox, 0)
            .addLabeledComponent("Version", versionComboBox, 2)
            .addLabeledComponent("Language Level", languageLevelLabel, 2)
            .addLabeledComponent("Interpreter", interpreter, 2)
            .addLabeledComponent("Source path patterns", sourcePath, 2)
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
        val platform = platformComboBox.selectedItem as? LuaPlatform ?: return
        val version = versionComboBox.selectedItem as? VersionEntry ?: return
        val newTarget = Target(platform, version)
        val previousTarget = state.getTarget()
        state.setTarget(newTarget)
        state.interpreter = interpreter.selectedItem as? LuaInterpreter
        state.sourcePath = sourcePath.text
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
    }

    fun isModified(data: LuaProjectSettings.State): Boolean {
        val savedTarget = data.getTarget()
        val currentPlatform = platformComboBox.selectedItem as? LuaPlatform ?: return false
        val currentVersion = versionComboBox.selectedItem as? VersionEntry ?: return false
        if (currentPlatform != savedTarget.platform) return true
        if (currentVersion != savedTarget.version) return true
        if (interpreter.selectedItem != data.interpreter) return true
        if (sourcePath.text != data.sourcePath) return true
        return false
    }
}