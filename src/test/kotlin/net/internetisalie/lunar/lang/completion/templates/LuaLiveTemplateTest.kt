package net.internetisalie.lunar.lang.completion.templates

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.codeInsight.template.impl.TemplateSettings

class LuaLiveTemplateTest : BasePlatformTestCase() {

    fun testLiveTemplatesLoaded() {
        val templates = TemplateSettings.getInstance().templates
        val luaTemplates = templates.filter { it.groupName == "Lua" }
        
        assertNotEmpty(luaTemplates)
        
        val templateNames = luaTemplates.map { it.key }
        assertContainsElements(templateNames, "fun", "fori", "forp", "loc")
    }
}
