package net.internetisalie.lunar.lang.surround

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Drives the real Surround With action (Ctrl+Alt+T) through [SurroundWithHandler.invoke] over the seven
 * EDITOR-05 surrounders, asserting the wrapped/re-indented result and caret placement (TC-1..TC-7), plus
 * the descriptor's whole-statement gating (partial / nested-block negatives). Real-flow DoD gate.
 */
class LuaSurroundWithTest : BasePlatformTestCase() {

    private fun surround(surrounder: Surrounder, source: String) {
        myFixture.configureByText("a.lua", source)
        SurroundWithHandler.invoke(myFixture.project, myFixture.editor, myFixture.file, surrounder)
    }

    private fun result(): String = myFixture.editor.document.text

    private fun caret(): Int = myFixture.editor.caretModel.offset

    /** The caret sits immediately after [keyword] (header templates place it in the construct's header). */
    private fun assertCaretAfter(keyword: String) {
        val before = result().substring(0, caret()).trimEnd()
        assertTrue("expected caret after '$keyword', doc=<${result()}> caret=${caret()}", before.endsWith(keyword))
    }

    /** The caret sits exactly at the start of the wrapped body (body templates). */
    private fun assertCaretAtBodyStart(firstToken: String) {
        val after = result().substring(caret())
        assertTrue("expected caret at body '$firstToken', doc=<${result()}> caret=${caret()}", after.startsWith(firstToken))
    }

    private fun assertWrapsBody(vararg fragments: String) {
        for (fragment in fragments) assertTrue("expected '$fragment' in <${result()}>", result().contains(fragment))
    }

    // TC-1 (EDITOR-05-01) — wrap two statements in `if <caret> then … end`.
    fun testIfWrapsStatementRunWithCaretInCondition() {
        surround(LuaIfSurrounder(), "<selection>foo()\nbar()</selection>")
        assertTrue("expected leading 'if', got <${result()}>", result().trimStart().startsWith("if"))
        assertWrapsBody("foo()", "bar()", "then")
        assertTrue("expected trailing 'end', got <${result()}>", result().trimEnd().endsWith("end"))
        assertCaretAfter("if")
    }

    // TC-2 (EDITOR-05-02) — `while <caret> do … end`.
    fun testWhileWrapsWithCaretInCondition() {
        surround(LuaWhileSurrounder(), "<selection>foo()</selection>")
        assertWrapsBody("while", "do", "foo()", "end")
        assertCaretAfter("while")
    }

    // TC-3 (EDITOR-05-02) — numeric `for <caret> = 1, 10 do … end`.
    fun testNumericForWrapsWithCaretAtLoopVar() {
        surround(LuaNumericForSurrounder(), "<selection>foo()</selection>")
        assertWrapsBody("for", "= 1, 10", "do", "foo()", "end")
        assertCaretAfter("for")
    }

    // TC-4 (EDITOR-05-02) — generic `for <caret> in pairs(t) do … end`.
    fun testGenericForWrapsWithCaretAtLoopVar() {
        surround(LuaGenericForSurrounder(), "<selection>foo()</selection>")
        assertWrapsBody("for", "in pairs(t)", "do", "foo()", "end")
        assertCaretAfter("for")
    }

    // TC-5 (EDITOR-05-03) — anonymous `function() <caret>… end`.
    fun testFunctionWrapsWithCaretAtBody() {
        surround(LuaFunctionSurrounder(), "<selection>foo()</selection>")
        assertWrapsBody("function()", "foo()", "end")
        assertCaretAtBodyStart("foo()")
    }

    // TC-6 (EDITOR-05-04) — bare `do <caret>… end`.
    fun testDoWrapsWithCaretAtBody() {
        surround(LuaDoSurrounder(), "<selection>foo()</selection>")
        assertWrapsBody("do", "foo()", "end")
        assertCaretAtBodyStart("foo()")
    }

    // TC-7 (EDITOR-05-05) — `pcall(function() <caret>… end)`.
    fun testPcallWrapsWithCaretAtBody() {
        surround(LuaPcallSurrounder(), "<selection>foo()</selection>")
        assertWrapsBody("pcall(function()", "foo()", "end)")
        assertCaretAtBodyStart("foo()")
    }

    // Negative — a selection that splits a statement offers nothing (whole-statement gating).
    fun testPartialSelectionInsideStatementOffersNoElements() {
        myFixture.configureByText("a.lua", "foo(<selection>a, b</selection>)")
        assertDescriptorEmptyAtSelection()
    }

    // Negative — a selection spanning an inner block boundary offers nothing (design §6).
    fun testSelectionSpanningNestedBlockOffersNoElements() {
        myFixture.configureByText("a.lua", "if a then\n<selection>x()\nend\ny()</selection>")
        assertDescriptorEmptyAtSelection()
    }

    // Positive — a clean two-statement selection yields exactly those two statements.
    fun testCleanTwoStatementSelectionYieldsBothStatements() {
        myFixture.configureByText("a.lua", "<selection>foo()\nbar()</selection>")
        val elements = elementsAtSelection()
        assertEquals("expected two whole statements, got ${elements.map { it.text }}", 2, elements.size)
    }

    private fun elementsAtSelection() =
        LuaStatementsSurroundDescriptor().getElementsToSurround(
            myFixture.file,
            myFixture.editor.selectionModel.selectionStart,
            myFixture.editor.selectionModel.selectionEnd,
        )

    private fun assertDescriptorEmptyAtSelection() {
        val elements = elementsAtSelection()
        assertEquals("expected no surroundable elements, got ${elements.map { it.text }}", 0, elements.size)
    }
}
