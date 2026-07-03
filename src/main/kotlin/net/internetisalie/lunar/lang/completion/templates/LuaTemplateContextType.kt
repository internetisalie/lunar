package net.internetisalie.lunar.lang.completion.templates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import net.internetisalie.lunar.lang.psi.LuaFile

// MAINT-03: the single-arg TemplateContextType(name) ctor leaves contextId null, and the
// <liveTemplateContext> EP registration carries no contextId attribute, so getContextId()
// would assert-fail at runtime. Migrating cleanly needs a plugin.xml contextId="LUA" (out of
// scope), so keep the deprecated two-arg ctor that sets the "LUA" contextId directly.
@Suppress("DEPRECATION")
class LuaTemplateContextType : TemplateContextType("LUA", "Lua") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        return templateActionContext.file is LuaFile
    }
}
