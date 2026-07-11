package net.internetisalie.lunar.lang.insight

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Drives the real Extend Selection (Ctrl+W) / Shrink Selection (Ctrl+Shift+W) editor actions over
 * the four EDITOR-04 [com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler] handlers,
 * asserting the selected text after each ladder step (TC-01..TC-08 + DR-01 fuzz).
 */
class LuaWordSelectionTest : BasePlatformTestCase() {

    private fun configure(source: String) {
        myFixture.configureByText("a.lua", source)
    }

    private fun extendOnce(): String? {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
        return myFixture.editor.selectionModel.selectedText
    }

    private fun shrinkOnce(): String? {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_UNSELECT_WORD_AT_CARET)
        return myFixture.editor.selectionModel.selectedText
    }

    /** Distinct, growing selection rungs; stops when the selection stops changing (never loops). */
    private fun extendLadder(maxSteps: Int = 15): List<String> = walk(maxSteps, ::extendOnce)

    private fun shrinkLadder(maxSteps: Int = 15): List<String> = walk(maxSteps, ::shrinkOnce)

    private fun walk(maxSteps: Int, step: () -> String?): List<String> {
        val rungs = mutableListOf<String>()
        repeat(maxSteps) {
            val selected = step() ?: return rungs
            if (rungs.lastOrNull() == selected) return rungs
            rungs.add(selected)
        }
        return rungs
    }

    // TC-01 — construct ladder (EDITOR-04-01): the body-statements rung is the Lunar contribution.
    fun testConstructLadderClimbsThroughBlockBody() {
        configure("local function f() local a = print(x<caret>, y) end")
        val ladder = extendLadder()
        for (rung in listOf("x", "x, y", "(x, y)", "print(x, y)", "local a = print(x, y)")) {
            assertTrue("expected rung '$rung', got $ladder", ladder.contains(rung))
        }
        assertTrue("expected enclosing function rung, got $ladder", ladder.any { it.startsWith("local function") && it.endsWith("end") })
    }

    // TC-08 (shrink) — Ctrl+Shift+W walks the ladder back down through the same rungs.
    fun testShrinkWalksLadderBackDown() {
        configure("local function f() local a = print(x<caret>, y) end")
        assertTrue("expected an ascending ladder", extendLadder().size > 1)
        val descending = shrinkLadder()
        assertTrue("expected shrink to reach 'x, y', got $descending", descending.contains("x, y"))
        assertTrue("expected shrink to reach 'x', got $descending", descending.contains("x"))
    }

    // TC-02 — string interior then full (EDITOR-04-02).
    fun testStringInteriorThenFullLiteral() {
        configure("local s = \"hel<caret>lo\"")
        val ladder = extendLadder()
        assertTrue("expected content 'hello', got $ladder", ladder.contains("hello"))
        assertTrue("expected full '\"hello\"', got $ladder", ladder.contains("\"hello\""))
    }

    // TC-05 — long string interior then full (EDITOR-04-02).
    fun testLongStringInteriorThenFull() {
        configure("local s = [[ra<caret>w]]")
        val ladder = extendLadder()
        assertTrue("expected content 'raw', got $ladder", ladder.contains("raw"))
        assertTrue("expected full '[[raw]]', got $ladder", ladder.contains("[[raw]]"))
    }

    // TC-03 — call argument list (EDITOR-04-03): item, then all-items, then bracketed.
    fun testArgumentListItemThenAllThenBracketed() {
        configure("f(a, b<caret>, c)")
        val ladder = extendLadder()
        for (rung in listOf("b", "a, b, c", "(a, b, c)")) {
            assertTrue("expected rung '$rung', got $ladder", ladder.contains(rung))
        }
    }

    // TC-06 — table constructor field list (EDITOR-04-03).
    fun testTableFieldListItemThenAllThenBraced() {
        configure("local t = {1, 2<caret>, 3}")
        val ladder = extendLadder()
        for (rung in listOf("2", "1, 2, 3", "{1, 2, 3}")) {
            assertTrue("expected rung '$rung', got $ladder", ladder.contains(rung))
        }
    }

    // TC-04 — short comment interior then full (EDITOR-04-04): '--' + spaces stripped.
    fun testShortCommentInteriorThenFull() {
        configure("-- a no<caret>te")
        val ladder = extendLadder()
        assertTrue("expected interior 'a note', got $ladder", ladder.contains("a note"))
        assertTrue("expected full '-- a note', got $ladder", ladder.contains("-- a note"))
    }

    // TC-07 — long comment interior then full (EDITOR-04-04): leveled markers stripped.
    fun testLongCommentInteriorThenFull() {
        configure("--[==[ blo<caret>ck ]==]")
        val ladder = extendLadder()
        assertTrue("expected interior ' block ', got $ladder", ladder.contains(" block "))
        assertTrue("expected full comment, got $ladder", ladder.contains("--[==[ block ]==]"))
    }

    // DR-01 — malformed literals / comments must never throw during the editor action.
    fun testMalformedLiteralsAndCommentsDoNotThrow() {
        val cases = listOf(
            "local s = \"ab<caret>c", // unterminated short string
            "local s = [<caret>[", // unterminated long string opener
            "--<caret>[", // truncated long-comment opener
            "-<caret>-", // bare short comment, no text
            "local s = \"<caret>\"", // empty string
            "local s = [[<caret>]]", // empty long string
        )
        for (source in cases) {
            configure(source)
            extendLadder() // must complete without throwing
        }
    }
}
