package net.internetisalie.lunar.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform

@Service(Service.Level.PROJECT)
@State(name = "LuaProjectSettings", storages = [Storage("LuaProjectSettings.xml")])
class LuaProjectSettings: PersistentStateComponent<LuaProjectSettings.State> {
    class State {
        var languageLevel : LuaLanguageLevel = LuaLanguageLevel.LUA54
        var platform : LuaPlatform = LuaPlatform.PUC;
    }

    private var myState = State()

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        val instance: LuaProjectSettings
            get() = ApplicationManager.getApplication()
                .getService(LuaProjectSettings::class.java)
    }
}
