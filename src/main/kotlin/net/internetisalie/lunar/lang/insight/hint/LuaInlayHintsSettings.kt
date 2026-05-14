package net.internetisalie.lunar.lang.insight.hint

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level settings for inlay hints in Lua files.
 * Custom settings that are not handled by the platform's declarative options.
 */
@Service(Service.Level.APP)
@State(
    name = "LuaInlayHintsSettings",
    storages = [Storage("lunar_inlay_hints.xml")],
    category = SettingsCategory.PLUGINS,
)
class LuaInlayHintsSettings : PersistentStateComponent<LuaInlayHintsSettings.State> {
    /**
     * State class for storing custom inlay hint preferences.
     */
    class State {
        var largeFileThreshold: Int = 10000
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        val instance: LuaInlayHintsSettings
            get() = ApplicationManager.getApplication()
                .getService(LuaInlayHintsSettings::class.java)
    }
}
