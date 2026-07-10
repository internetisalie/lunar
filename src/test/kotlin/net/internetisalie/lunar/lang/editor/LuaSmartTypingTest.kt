package net.internetisalie.lunar.lang.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Smart-typing test suite (EDITOR-01). Covers TC-1 through TC-12 from implementation-plan.md.
 *
 * Phase 1 (TC-1..TC-7): quote pairing, bracket suppression.
 * Phase 2 (TC-8..TC-12): keyword-block auto-closer + settings toggle — added after Phase 2 lands.
 *
 * Uses [BasePlatformTestCase] + [myFixture] for a lightweight in-process editor harness.
 * Structural assertions follow the same strategy as LuaEnterHandlerTest: text content and
 * caret position, not exact indent columns (known harness quirk; live-IDE columns checked manually).
 */
class LuaSmartTypingTest : BasePlatformTestCase() {

    // --- TC-1: bracket auto-close (EDITOR-01-01) ---

    // TC-1: bracket auto-close delivered by platform + brace-matcher.
    fun testBracketAutoClose() {
        myFixture.configureByText("test.lua", "print<caret>")
        myFixture.type('(')
        val text = myFixture.editor.document.text
        assertTrue("auto-inserted ')' present", text.contains("print()"))
    }

    // --- TC-2: bracket skip-closer (EDITOR-01-02) ---

    // TC-2: typing ')' over an existing auto-inserted closer skips instead of duplicating.
    fun testBracketSkipCloser() {
        myFixture.configureByText("test.lua", "print<caret>")
        myFixture.type('(')
        myFixture.type(')')
        val text = myFixture.editor.document.text
        assertEquals("single pair — no duplicate '('", 1, text.count { it == '(' })
        assertEquals("single pair — no duplicate ')'", 1, text.count { it == ')' })
    }

    // --- TC-3: quote auto-close open (EDITOR-01-03) ---

    // TC-3: typing `"` at value position inserts the closing quote.
    fun testQuoteAutoClose() {
        myFixture.configureByText("test.lua", "local s = <caret>")
        myFixture.type('"')
        val text = myFixture.editor.document.text
        assertEquals("closing quote auto-inserted: two quotes total", 2, text.count { it == '"' })
    }

    // --- TC-4: quote skip-closer (EDITOR-01-03) ---

    // TC-4: typing `"` over the closing quote skips — still one pair.
    fun testQuoteSkipCloser() {
        myFixture.configureByText("test.lua", "local s = <caret>")
        myFixture.type('"')
        myFixture.type('"')
        val text = myFixture.editor.document.text
        assertEquals("skip: still one pair, two quote chars", 2, text.count { it == '"' })
        val caretOffset = myFixture.editor.caretModel.offset
        val lastQuote = text.lastIndexOf('"')
        assertEquals("caret moved past the closing quote", lastQuote + 1, caretOffset)
    }

    // --- TC-5: backspace-unpair (EDITOR-01-04 quote) ---

    // TC-5: Backspace after a fresh empty `""` pair deletes both quotes.
    fun testQuoteBackspaceUnpair() {
        myFixture.configureByText("test.lua", "local s = <caret>")
        myFixture.type('"')
        // Document is now `local s = "<caret>"` — Backspace should remove both.
        myFixture.type('\b')
        val text = myFixture.editor.document.text
        assertEquals("both quotes removed by backspace-unpair", 0, text.count { it == '"' })
    }

    // --- TC-6: bracket suppressed in string / comment (EDITOR-01-01 context-aware) ---

    // TC-6a: bracket inside `-- comment` is NOT auto-closed.
    fun testBracketSuppressedInLineComment() {
        myFixture.configureByText("test.lua", "-- hello<caret>")
        myFixture.type('(')
        val text = myFixture.editor.document.text
        assertEquals("no auto ')' inside a line comment", 0, text.count { it == ')' })
    }

    // TC-6b: bracket inside a string literal is NOT auto-closed.
    fun testBracketSuppressedInString() {
        myFixture.configureByText("test.lua", "local s = \"hello<caret>\"")
        myFixture.type('(')
        val text = myFixture.editor.document.text
        assertEquals("no auto ')' inside a string literal", 0, text.count { it == ')' })
    }

    // --- TC-7: mid-word quote suppression (EDITOR-01-03) ---

    // TC-7: typing `'` immediately after an identifier char does NOT auto-close.
    fun testQuoteNoAutoCloseAfterIdentifierChar() {
        myFixture.configureByText("test.lua", "-- don<caret>")
        myFixture.type('\'')
        val text = myFixture.editor.document.text
        assertEquals("no auto-close: exactly one quote typed", 1, text.count { it == '\'' })
    }
}
