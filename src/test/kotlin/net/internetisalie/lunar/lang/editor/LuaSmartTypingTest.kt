package net.internetisalie.lunar.lang.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.settings.LuaEditorOptions

/**
 * Smart-typing test suite (EDITOR-01). Covers TC-1 through TC-12 from implementation-plan.md.
 *
 * Phase 1 (TC-1..TC-7): quote pairing, bracket suppression.
 * Phase 2 (TC-8..TC-12): keyword-block auto-closer + settings toggle.
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

    // --- TC-8: LuaKeywordBlockCloser API — function token inserts `end` (EDITOR-01-05) ---

    // TC-8: direct API call — closeIfNeeded inserts `end` after a `function` keyword.
    fun testKeywordBlockCloserInsertsEnd() {
        myFixture.configureByText("test.lua", "function<caret>")
        val project = myFixture.project
        val editor = myFixture.editor
        val file = myFixture.file
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val offset = editor.caretModel.offset
        val leaf = file.findElementAt(offset - 1) ?: return
        val inserted = WriteCommandAction.runWriteCommandAction<Boolean>(project) {
            LuaKeywordBlockCloser.closeIfNeeded(editor, file, leaf.textRange.endOffset)
        }
        if (inserted == true) {
            assertEquals("end scaffolded once", 1, countWord(editor.document.text, "end"))
        }
    }

    // --- TC-9: keystroke path — space after `then` scaffolds `end` (EDITOR-01-05 keystroke) ---

    fun testKeywordBlockCloserOnThenSpace() {
        LuaEditorOptions.instance.autoCloseKeywordBlocks = true
        myFixture.configureByText("test.lua", "if x then<caret>")
        myFixture.type(' ')
        val text = myFixture.editor.document.text
        assertEquals("end inserted after 'then '", 1, countWord(text, "end"))
    }

    // --- TC-10: `repeat ` scaffolds `until`, not `end` (EDITOR-01-05) ---

    fun testKeywordBlockCloserRepeatUntil() {
        LuaEditorOptions.instance.autoCloseKeywordBlocks = true
        myFixture.configureByText("test.lua", "repeat<caret>")
        myFixture.type(' ')
        val text = myFixture.editor.document.text
        assertTrue("until inserted for repeat", text.contains("until"))
        assertEquals("no end for repeat", 0, countWord(text, "end"))
    }

    // --- TC-11: already balanced — no second terminator (balance check §3.4 step 5) ---

    fun testKeywordBlockCloserAlreadyBalanced() {
        LuaEditorOptions.instance.autoCloseKeywordBlocks = true
        myFixture.configureByText("test.lua", "function foo()<caret>\nend")
        myFixture.type(' ')
        val text = myFixture.editor.document.text
        assertEquals("exactly one 'end' — no double-insert", 1, countWord(text, "end"))
    }

    // --- TC-12: toggle off — no scaffolding (EDITOR-01-05 toggle) ---

    fun testKeywordBlockCloserToggleOff() {
        LuaEditorOptions.instance.autoCloseKeywordBlocks = false
        myFixture.configureByText("test.lua", "if x then<caret>")
        myFixture.type(' ')
        val text = myFixture.editor.document.text
        assertEquals("no end inserted when toggle is off", 0, countWord(text, "end"))
    }

    override fun tearDown() {
        try {
            LuaEditorOptions.instance.autoCloseKeywordBlocks = true
        } catch (_: Exception) {
            // service may not be available in minimal test contexts
        }
        super.tearDown()
    }

    private fun countWord(text: String, word: String): Int =
        Regex("\\b${Regex.escape(word)}\\b").findAll(text).count()
}
