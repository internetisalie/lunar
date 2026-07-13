package net.internetisalie.lunar.lang.editor

import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Drives the real Move Statement Up/Down actions (Ctrl+Shift+↑/↓) over [LuaStatementMover], asserting the
 * full document text after each move (TC-01a..c sibling/no-op, TC-02a/b enter/leave block). The line-mover
 * fallback (TC-04) is asserted at the contract level — `checkAvailable` returns `false` so the platform runs
 * its own `LineMover` (whose exact output is platform-owned). Uses 4-space bodies to match Lunar's default
 * indent. Real-flow DoD gate. EDITOR-07-01/-02/-04.
 */
class LuaStatementMoverTest : BasePlatformTestCase() {

    private fun down() = myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)

    private fun up() = myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)

    private fun configure(source: String) = myFixture.configureByText("a.lua", source)

    private fun structuralMoveAvailable(down: Boolean): Boolean {
        val info = StatementUpDownMover.MoveInfo()
        return LuaStatementMover().checkAvailable(myFixture.editor, myFixture.file, info, down)
    }

    // TC-01a — swap two sibling statements down, then up round-trips.
    fun testSwapSiblingsDownAndUp() {
        configure("local a = 1<caret>\nlocal b = 2\n")
        down()
        myFixture.checkResult("local b = 2\nlocal a = 1\n")
        up()
        myFixture.checkResult("local a = 1\nlocal b = 2\n")
    }

    // TC-01b — the only statement of a repeat body never displaces `until` (prohibited → no-op).
    fun testRepeatBodyDoesNotDisplaceUntil() {
        configure("repeat\n    print(1)<caret>\nuntil x")
        down()
        myFixture.checkResult("repeat\n    print(1)\nuntil x")
    }

    // TC-01c — a lone top-level statement is a no-op.
    fun testLoneStatementIsNoOp() {
        configure("local a = 1<caret>")
        up()
        myFixture.checkResult("local a = 1")
        down()
        myFixture.checkResult("local a = 1")
    }

    // TC-02a — moving down into an adjacent `if` enters its body (first statement), re-indented.
    fun testMoveDownIntoIfBody() {
        configure("print(1)<caret>\nif x then\n    print(2)\nend")
        down()
        myFixture.checkResult("if x then\n    print(1)\n    print(2)\nend")
    }

    // TC-02b — moving down out of an `if` body steps over `end`, de-indented.
    fun testMoveDownOutOfIfBody() {
        configure("if x then\n    print(1)<caret>\nend\nprint(2)")
        down()
        myFixture.checkResult("if x then\nend\nprint(1)\nprint(2)")
    }

    // TC-04a — caret on a blank line: no structural move → platform LineMover fallback runs.
    fun testBlankLineDefersToLineMover() {
        configure("local a = 1\n<caret>\nlocal b = 2")
        assertFalse("blank line must defer to LineMover", structuralMoveAvailable(down = true))
    }

    // TC-04b — caret inside a multi-line long string: defer to LineMover (never split the literal).
    fun testMultilineStringDefersToLineMover() {
        configure("local s = [[\nab<caret>cd\n]]\nlocal t = 2")
        assertFalse("multi-line string must defer to LineMover", structuralMoveAvailable(down = false))
    }
}
