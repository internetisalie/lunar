package net.internetisalie.lunar.analysis.luacheck

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.text.StringUtil

@Service(Service.Level.APP)
@State(
    name = "LuaCheckSettings",
    storages = [Storage("lunar.xml")],
    category = SettingsCategory.PLUGINS,
)
class LuaCheckSettings : SimplePersistentStateComponent<LuaCheckSettings.State>(State()) {
    class State : BaseState() {
        var executablePath by string("/usr/local/bin/luacheck")
        var arguments by string("")
    }

    var executablePath : String
        get() = StringUtil.notNullize(state.executablePath)
        set(value) { state.executablePath = value }

    var arguments : String
        get() = StringUtil.notNullize(state.arguments)
        set(value) { state.arguments = value }

    companion object {
        fun getInstance(): LuaCheckSettings {
            return ApplicationManager.getApplication()
                .getService(LuaCheckSettings::class.java)
        }
    }
}

