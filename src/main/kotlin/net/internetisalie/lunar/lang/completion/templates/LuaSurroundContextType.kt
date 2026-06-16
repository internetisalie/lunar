package net.internetisalie.lunar.lang.completion.templates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

/**
 * Selection-aware Lua live-template context for Surround-With templates (COMP-07-11). The platform
 * only consults surround contexts when a selection exists, so it reuses the code predicate at the
 * selection start. Parented to [LuaTemplateContextType] (`LUA`).
 */
@Suppress("DEPRECATION")
class LuaSurroundContextType :
    TemplateContextType("LUA_SURROUND", "Lua (surround)", LuaTemplateContextType::class.java) {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        return isInLuaCodeContext(templateActionContext)
    }
}
