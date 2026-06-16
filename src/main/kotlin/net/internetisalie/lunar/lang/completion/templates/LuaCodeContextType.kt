package net.internetisalie.lunar.lang.completion.templates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

/**
 * Code-aware Lua live-template context (COMP-07-10): true only when the caret is in real Lua code,
 * not inside a string, comment, or number. Parented to [LuaTemplateContextType] (`LUA`).
 */
@Suppress("DEPRECATION")
class LuaCodeContextType :
    TemplateContextType("LUA_CODE", "Lua (code)", LuaTemplateContextType::class.java) {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        return isInLuaCodeContext(templateActionContext)
    }
}
