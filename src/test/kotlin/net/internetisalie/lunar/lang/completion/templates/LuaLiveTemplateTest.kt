package net.internetisalie.lunar.lang.completion.templates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFile

class LuaLiveTemplateTest : BasePlatformTestCase() {

    fun testLiveTemplatesLoaded() {
        val templates = TemplateSettings.getInstance().templates
        val luaTemplates = templates.filter { it.groupName == "Lua" }

        assertNotEmpty(luaTemplates)

        val templateNames = luaTemplates.map { it.key }
        assertContainsElements(
            templateNames,
            "fun", "fori", "forp", "loc",
            "if", "ifel", "lfun", "while",
            "repeat", "forip", "req", "mod",
            "surr_if", "surr_for", "surr_do", "surr_fn",
        )
    }

    fun testIfTemplateExpandsInCode() {
        myFixture.configureByText("a.lua", "if<caret>")
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            myFixture.type('\t')
        }
        val text = myFixture.file.text
        assertTrue("expected expanded if/then/end, got: $text", text.contains("then") && text.contains("end"))
    }

    fun testWhileTemplateExpandsInCode() {
        myFixture.configureByText("a.lua", "while<caret>")
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            myFixture.type('\t')
        }
        val text = myFixture.file.text
        assertTrue("expected expanded while/do/end, got: $text", text.contains("do") && text.contains("end"))
    }

    fun testCodeContextSuppressedInString() {
        assertContextAtCaret("local s = \"for<caret>p\"", expectedInCode = false)
    }

    fun testCodeContextSuppressedInComment() {
        assertContextAtCaret("-- for<caret>p", expectedInCode = false)
    }

    fun testCodeContextSuppressedInNumber() {
        assertContextAtCaret("local x = 1<caret>0", expectedInCode = false)
    }

    fun testCodeContextSuppressedInLongString() {
        assertContextAtCaret("local s = [[ for<caret>p ]]", expectedInCode = false)
    }

    fun testCodeContextActiveInRealCode() {
        assertContextAtCaret("local x = 1\nif<caret>", expectedInCode = true)
    }

    private fun assertContextAtCaret(source: String, expectedInCode: Boolean) {
        myFixture.configureByText("a.lua", source)
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val offset = myFixture.caretOffset
            val file = myFixture.file as LuaFile
            val context = TemplateActionContext.expanding(file, offset)
            val codeContext = LuaCodeContextType()
            assertEquals(
                "LuaCodeContextType.isInContext at offset $offset of <$source>",
                expectedInCode,
                codeContext.isInContext(context),
            )
        }
    }
}
