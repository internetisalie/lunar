package net.internetisalie.lunar.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.customizeLuaInterpreterComboBox
import net.internetisalie.lunar.project.PlatformLibraryIndex
import javax.swing.JComboBox
import javax.swing.JPanel

class LuaProjectSettingsPanel(val project: Project) {
    val mainPanel: JPanel
    private val platform: JComboBox<LuaPlatform>
    private val languageLevel: JComboBox<LuaLanguageLevel>
    private val interpreter: JComboBox<LuaInterpreter>
    private val sourcePath: ExpandableTextField

    init {
        platform = ComboBox<LuaPlatform>(
            arrayOf(
                LuaPlatform.STANDARD,
                LuaPlatform.LUAU,
                LuaPlatform.PANDOC,
                LuaPlatform.REDIS,
                LuaPlatform.TARANTOOL,
            )
        )

        languageLevel = ComboBox<LuaLanguageLevel>(
            arrayOf(
                LuaLanguageLevel.LUA50,
                LuaLanguageLevel.LUA51,
                LuaLanguageLevel.LUA52,
                LuaLanguageLevel.LUA53,
                LuaLanguageLevel.LUA54,
            )
        )

        interpreter = ComboBox<LuaInterpreter>()
        customizeLuaInterpreterComboBox(project, interpreter)

        sourcePath = ExpandableTextField(
            { value -> value.split(PathConfiguration.TEMPLATE_SEPARATOR) },
            { entries -> entries.joinToString(PathConfiguration.TEMPLATE_SEPARATOR) },
        )
        sourcePath.columns = 60
        sourcePath.text = PathConfiguration.DEFAULT_SOURCE_PATH

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Platform", platform, 0)
            .addLabeledComponent("Language level", languageLevel, 2)
            .addLabeledComponent("Interpreter", interpreter, 2)
            .addLabeledComponent("Source path patterns", sourcePath, 2)
            .addComponentFillVertically(JPanel(), 2)
            .panel
    }

    fun apply(state: LuaProjectSettings.State) {
        val originalLanguageLevel = state.languageLevel
        getData(state)
        if (state.languageLevel !== originalLanguageLevel) {
            PlatformLibraryIndex.reload()
        }
    }

    fun reset() {
        setData(LuaProjectSettings.getInstance(project).state)
    }

    fun setData(data: LuaProjectSettings.State) {
        languageLevel.selectedItem = data.languageLevel
        platform.selectedItem = data.platform
        interpreter.selectedItem = data.interpreter
        sourcePath.text = data.sourcePath
    }

    fun getData(data: LuaProjectSettings.State) {
        data.platform = platform.selectedItem as LuaPlatform
        data.languageLevel = languageLevel.selectedItem as LuaLanguageLevel
        data.interpreter = interpreter.selectedItem as LuaInterpreter
        data.sourcePath = sourcePath.text
    }

    fun isModified(data: LuaProjectSettings.State): Boolean {
        if (platform.selectedItem != data.platform) return true
        if (languageLevel.selectedItem != data.languageLevel) return true
        if (interpreter.selectedItem != data.interpreter) return true
        if (sourcePath.text != data.sourcePath) return true
        return false
    }
}