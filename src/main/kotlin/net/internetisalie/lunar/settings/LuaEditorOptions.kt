package net.internetisalie.lunar.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level persisted options for Lua editor smart-typing behavior.
 *
 * Currently holds [autoCloseKeywordBlocks], the on/off toggle for keyword-block
 * terminator insertion (EDITOR-01-05). Exposed to Settings > Editor > General > Smart Keys
 * via [net.internetisalie.lunar.lang.editor.LuaEditorOptionsConfigurable].
 *
 * Mirrors the pattern from [LuaApplicationSettings] and Kotlin's `KotlinEditorOptions`.
 */
@Service(Service.Level.APP)
@State(
    name = "LuaEditorOptions",
    storages = [Storage("lunar.editor.xml")],
    category = SettingsCategory.CODE,
)
class LuaEditorOptions : PersistentStateComponent<LuaEditorOptions.State> {

    class State {
        var autoCloseKeywordBlocks: Boolean = true
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var autoCloseKeywordBlocks: Boolean
        get() = myState.autoCloseKeywordBlocks
        set(value) {
            myState.autoCloseKeywordBlocks = value
        }

    companion object {
        val instance: LuaEditorOptions
            get() = ApplicationManager.getApplication().getService(LuaEditorOptions::class.java)
    }
}
