package net.internetisalie.lunar.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaPlatform

@Service(Service.Level.PROJECT)
@State(
    name = "LuaProjectSettings",
    storages = [Storage("lunar.xml")],
    category = SettingsCategory.PLUGINS,
)
class LuaProjectSettings: PersistentStateComponent<LuaProjectSettings.State> {
    class State {
        var languageLevel : LuaLanguageLevel = LuaLanguageLevel.LUA54
        var platform : LuaPlatform = LuaPlatform.STANDARD;
        var interpreter: LuaInterpreter? = null
        var sourcePath: String = PathConfiguration.DEFAULT_SOURCE_PATH

        fun expandSourcePath(project : Project) : String {
            return sourcePath.trim(' ').expandMacros(project)
        }
    }

    private var myState = State()

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): LuaProjectSettings {
            return project.getService(LuaProjectSettings::class.java)
        }
    }
}

fun String.expandMacros(project: Project) : String {
    return PathMacroManager
        .getInstance(project)
        .expandPath(this)
}
