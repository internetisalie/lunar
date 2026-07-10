package net.internetisalie.lunar.lang.editor

import com.intellij.openapi.options.BeanConfigurable
import net.internetisalie.lunar.settings.LuaEditorOptions

/**
 * Contributes a "Lua" section with one checkbox to Settings > Editor > General > Smart Keys.
 *
 * Checkbox: "Insert matching end/until for Lua block keywords" — controls
 * [LuaEditorOptions.autoCloseKeywordBlocks], which gates EDITOR-01-05.
 *
 * Registered via `<editorSmartKeysConfigurable instance="…"/>` (design §2.6, §7).
 * Mirrors Kotlin's `KotlinEditorOptionsConfigurable`.
 */
class LuaEditorOptionsConfigurable :
    BeanConfigurable<LuaEditorOptions>(LuaEditorOptions.instance, "Lua") {

    init {
        checkBox(
            "Insert matching end/until for Lua block keywords",
            LuaEditorOptions.instance::autoCloseKeywordBlocks,
        )
    }
}
