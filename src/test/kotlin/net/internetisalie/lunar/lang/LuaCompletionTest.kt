package net.internetisalie.lunar.lang

import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class LuaCompletionTest : BaseDocumentTest() {

    @Test
    fun testAttributeCompletion() {
        net.internetisalie.lunar.settings.LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = LuaLanguageLevel.LUA54
        myFixture.configureByText("test.lua", "local x <caret>")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertTrue(strings != null && strings.contains("<"), "Should suggest '<' (got $strings)")
    }

    @Test
    fun testAttributeNameCompletionWithSpaces() {
        net.internetisalie.lunar.settings.LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = LuaLanguageLevel.LUA54
        myFixture.configureByText("test.lua", "local x <  <caret>  >")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertTrue(strings != null && strings.contains("const"), "Should suggest 'const' (got $strings)")
        assertTrue(strings != null && strings.contains("close"), "Should suggest 'close' (got $strings)")
    }

    @Test
    fun testAttributeNameCompletion() {
        net.internetisalie.lunar.settings.LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = LuaLanguageLevel.LUA54
        myFixture.configureByText("test.lua", "local x <c<caret>>")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertTrue(strings != null && strings.contains("const"), "Should suggest 'const' (got $strings)")
        assertTrue(strings != null && strings.contains("close"), "Should suggest 'close' (got $strings)")
    }

    @Test
    fun testNoAttributeCompletionInLua53() {
        net.internetisalie.lunar.settings.LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = LuaLanguageLevel.LUA53
        myFixture.configureByText("test.lua", "local x <caret>")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertTrue(strings == null || !strings.contains("<"), "Should NOT suggest '<' (got $strings)")
    }

    @Test
    fun testNoAttributeNameCompletionInLua53() {
        net.internetisalie.lunar.settings.LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = LuaLanguageLevel.LUA53
        myFixture.configureByText("test.lua", "local x < <caret> >")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertTrue(strings == null || (!strings.contains("const") && !strings.contains("close")), "Should NOT suggest 'const'/'close' (got $strings)")
    }
}
