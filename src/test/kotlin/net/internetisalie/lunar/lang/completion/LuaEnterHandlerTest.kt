package net.internetisalie.lunar.lang.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LuaEnterHandlerTest : BasePlatformTestCase() {

    // TC 1 — Enter after `then` (COMP-08-01): single `end` inserted.
    fun testEnterAfterThen() {
        myFixture.configureByText("test.lua", "if x > 5 then<caret>")
        myFixture.type('\n')
        assertEquals(1, endCount())
        assertNoExtraTerminator("end")
    }

    // TC 6 — `while … do`.
    fun testEnterAfterWhileDo() {
        myFixture.configureByText("test.lua", "while x do<caret>")
        myFixture.type('\n')
        assertEquals(1, endCount())
    }

    // TC 6 — numeric `for … do`.
    fun testEnterAfterNumericForDo() {
        myFixture.configureByText("test.lua", "for i = 1, 10 do<caret>")
        myFixture.type('\n')
        assertEquals(1, endCount())
    }

    // TC 6 — generic `for … do`.
    fun testEnterAfterGenericForDo() {
        myFixture.configureByText("test.lua", "for k, v in pairs(t) do<caret>")
        myFixture.type('\n')
        assertEquals(1, endCount())
    }

    // TC 6 — bare `do`.
    fun testEnterAfterBareDo() {
        myFixture.configureByText("test.lua", "do<caret>")
        myFixture.type('\n')
        assertEquals(1, endCount())
    }

    // TC 6 — `repeat … until` (terminator is `until`, not `end`).
    fun testEnterAfterRepeat() {
        myFixture.configureByText("test.lua", "repeat<caret>")
        myFixture.type('\n')
        val text = myFixture.editor.document.text
        assertEquals("expected one 'until'", 1, occurrences(text, Regex("\\buntil\\b")))
        assertEquals("no 'end' for repeat", 0, occurrences(text, Regex("\\bend\\b")))
    }

    // Function opener (COMP-08-03).
    fun testEnterAfterFunctionKeyword() {
        myFixture.configureByText("test.lua", "function<caret>")
        myFixture.type('\n')
        assertEquals(1, endCount())
    }

    // TC 2 — COMP-08-02 bug fix: already-balanced block, NO second `end`.
    fun testEnterAfterThenAlreadyBalanced() {
        myFixture.configureByText("test.lua", "if true then<caret>\nend")
        myFixture.type('\n')
        assertEquals("exactly one 'end' must remain (no redundant insert)", 1, endCount())
    }

    // TC 3 — table-literal completion (COMP-08-03).
    fun testEnterAfterTableBrace() {
        myFixture.configureByText("test.lua", "local t = {<caret>")
        myFixture.type('\n')
        val text = myFixture.editor.document.text
        assertEquals("expected one closing '}'", 1, occurrences(text, Regex("\\}")))
        assertEquals("expected one opening '{'", 1, occurrences(text, Regex("\\{")))
    }

    // Table already balanced — no second `}`.
    fun testEnterAfterTableBraceAlreadyBalanced() {
        myFixture.configureByText("test.lua", "local t = {<caret>\n}")
        myFixture.type('\n')
        assertEquals("exactly one '}' must remain", 1, occurrences(myFixture.editor.document.text, Regex("\\}")))
    }

    // TC 4 — between-pair indent (COMP-08-04): caret between `function f()` and its `end`,
    // no terminator inserted.
    fun testEnterBetweenMatchedFunction() {
        myFixture.configureByText("test.lua", "function f()<caret>\nend")
        myFixture.type('\n')
        assertEquals("no second 'end' inserted between a matched pair", 1, endCount())
    }

    // COMP-08-05 — reformat/caret: the inserted terminator lands on its own line and the caret rests
    // on the body line, inside an outer block. NOTE: the in-process editor harness does not reindent
    // block bodies to the nested depth the real IDE produces (same quirk noted for COMP-06), so this
    // asserts structural correctness (one extra `end`, on its own line, caret on the body line) rather
    // than the exact indent column.
    fun testEnterReindentsBodyAndTerminator() {
        myFixture.configureByText("test.lua", "do\n    while x do<caret>\nend")
        myFixture.type('\n')
        val lines = myFixture.editor.document.text.lines()
        // Two `end`s total now: the pre-existing outer one and the freshly inserted inner one, each
        // on its own line.
        assertEquals("two terminators, one per block", 2, lines.count { it.trim() == "end" })
        val caretLine = myFixture.editor.document.getLineNumber(myFixture.editor.caretModel.offset)
        assertTrue("caret rests on a body line above a terminator", lines[caretLine + 1].trim() == "end")
    }

    // MAINT-28 TC-25 (#25): caret between `then` and `end` on one line. DefaultForceIndent fires and
    // opens a body line, splitting `then` and `end` across lines with no extra terminator inserted.
    // (In-process harness quirk, same as testEnterReindentsBodyAndTerminator: the body line is not
    // reindented to nested depth and the caret rests at the start of the terminator line, so this
    // asserts the structural split rather than an exact indent column.)
    fun testEnterBetweenThenAndEndSameLine() {
        myFixture.configureByText("test.lua", "if x then<caret>end")
        myFixture.type('\n')
        assertEquals("no second 'end' inserted between the matched pair", 1, endCount())
        val lines = myFixture.editor.document.text.lines()
        assertEquals("then and end split across two lines", 2, lines.size)
        assertTrue("first line retains the opener up to 'then'", lines[0].trimEnd().endsWith("then"))
        assertEquals("terminator on its own line", "end", lines[1].trim())
    }

    // TC-25 negative: Enter *after* `end` leaves default behavior (no forced body line above a
    // terminator, since the caret is past the terminator start).
    fun testEnterAfterEndIsDefault() {
        myFixture.configureByText("test.lua", "if x then\nend<caret>")
        myFixture.type('\n')
        assertEquals("still exactly one 'end'", 1, endCount())
    }

    private fun endCount(): Int = occurrences(myFixture.editor.document.text, Regex("\\bend\\b"))

    private fun assertNoExtraTerminator(keyword: String) {
        assertEquals("exactly one '$keyword'", 1, occurrences(myFixture.editor.document.text, Regex("\\b$keyword\\b")))
    }

    private fun occurrences(text: String, regex: Regex): Int = regex.findAll(text).count()
}
