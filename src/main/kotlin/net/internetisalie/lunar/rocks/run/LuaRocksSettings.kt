package net.internetisalie.lunar.rocks.run

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.text.StringUtil

/**
 * Application-wide settings for the LuaRocks integration.
 *
 * Persists the `luarocks` executable path so every LuaRocks feature (task execution / run
 * configs here, plus the planned ROCKS-02/03/08 integrations) resolves the binary from one
 * place. Modeled on [net.internetisalie.lunar.analysis.luacheck.LuaCheckSettings].
 *
 * The default value, [DEFAULT_EXECUTABLE], is the bare command name so the platform resolves it
 * on `PATH`; if it is missing, the run launcher surfaces a clear error.
 */
@Service(Service.Level.APP)
@State(
    name = "LuaRocksSettings",
    storages = [Storage("lunar.xml")],
    category = SettingsCategory.PLUGINS,
)
class LuaRocksSettings : SimplePersistentStateComponent<LuaRocksSettings.State>(State()) {
    class State : BaseState() {
        var executablePath by string(DEFAULT_EXECUTABLE)
    }

    var executablePath: String
        get() = StringUtil.notNullize(state.executablePath, DEFAULT_EXECUTABLE)
        set(value) { state.executablePath = value }

    companion object {
        const val DEFAULT_EXECUTABLE: String = "luarocks"

        fun getInstance(): LuaRocksSettings {
            return ApplicationManager.getApplication()
                .getService(LuaRocksSettings::class.java)
        }
    }
}
