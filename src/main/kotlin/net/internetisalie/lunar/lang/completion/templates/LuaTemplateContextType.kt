package net.internetisalie.lunar.lang.completion.templates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import net.internetisalie.lunar.lang.psi.LuaFile

class LuaTemplateContextType : TemplateContextType("LUA", "Lua") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        return templateActionContext.file is LuaFile
    }
}
