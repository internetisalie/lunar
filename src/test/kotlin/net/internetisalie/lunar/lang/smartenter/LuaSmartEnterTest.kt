package net.internetisalie.lunar.lang.smartenter

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Drives the real Smart Enter action (Ctrl+Shift+Enter / EditorCompleteStatement) over
 * [LuaSmartEnterProcessor]. Assertions are structural (keyword/bracket presence, single terminator, caret
 * between header and terminator) rather than exact whitespace, so they are robust to the formatter's indent
 * width. TC-01..TC-10. EDITOR-08.
 */
class LuaSmartEnterTest : BasePlatformTestCase() {

    private fun complete(source: String): String {
        myFixture.configureByText("a.lua", source)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT)
        return myFixture.editor.document.text
    }

    private fun caret(): Int = myFixture.editor.caretModel.offset

    private fun count(text: String, word: String): Int = Regex("\\b$word\\b").findAll(text).count()

    private fun assertClosedBlock(text: String, vararg headerKeywords: String) {
        for (kw in headerKeywords) assertTrue("expected '$kw' in <$text>", text.contains(kw))
        assertTrue("expected trailing 'end' in <$text>", text.trimEnd().endsWith("end"))
        assertEquals("exactly one 'end' in <$text>", 1, count(text, "end"))
    }

    private fun assertCaretInBody(text: String, terminator: String = "end") {
        assertTrue("caret before '$terminator' in <$text> @${caret()}", text.substring(caret()).contains(terminator))
    }

    // TC-01 (08-01) — if.
    fun testCompleteIf() {
        val text = complete("if x<caret>")
        assertClosedBlock(text, "if x", "then")
        assertCaretInBody(text)
    }

    // TC-03 (08-01) — while.
    fun testCompleteWhile() {
        val text = complete("while c<caret>")
        assertClosedBlock(text, "while c", "do")
        assertCaretInBody(text)
    }

    // TC-04 (08-01) — numeric for.
    fun testCompleteNumericFor() {
        val text = complete("for i = 1, n<caret>")
        assertClosedBlock(text, "for i = 1, n", "do")
        assertCaretInBody(text)
    }

    // TC-08 (08-01) — generic for.
    fun testCompleteGenericFor() {
        val text = complete("for k, v in pairs(t)<caret>")
        assertClosedBlock(text, "for k, v in pairs(t)", "do")
        assertCaretInBody(text)
    }

    // TC-05 (08-01) — function supplies () and end.
    fun testCompleteFunction() {
        val text = complete("function foo<caret>")
        assertClosedBlock(text, "function foo(", ")")
        assertEquals("balanced parens", count(text, "foo"), 1)
        assertTrue("has ()", text.contains("foo()"))
    }

    // TC-06 (08-03) — repeat completes with an until tail; caret at the condition.
    fun testCompleteRepeat() {
        val text = complete("repeat<caret>")
        assertTrue("has repeat", text.contains("repeat"))
        assertTrue("has until", text.contains("until"))
        assertEquals("no 'end' for repeat", 0, count(text, "end"))
        assertTrue("caret after until", text.substring(0, caret()).contains("until"))
    }

    // TC-07 (08-02) — balance a call's open paren.
    fun testBalanceCallParen() {
        val text = complete("print(\"x\"<caret>")
        assertTrue("balanced call in <$text>", text.contains("print(\"x\")"))
    }

    // TC-09 (08-02) — balance a table constructor's open brace.
    fun testBalanceTableBrace() {
        val text = complete("local t = { 1, 2<caret>")
        assertEquals("one '{'", 1, text.count { it == '{' })
        assertEquals("one '}'", 1, text.count { it == '}' })
    }

    // TC-10 (08-01) — an already-complete block is not double-completed.
    fun testIdempotentOnCompleteBlock() {
        val text = complete("if x then\n    body()<caret>\nend")
        assertEquals("still one 'end'", 1, count(text, "end"))
        assertEquals("still one 'then'", 1, count(text, "then"))
    }
}
