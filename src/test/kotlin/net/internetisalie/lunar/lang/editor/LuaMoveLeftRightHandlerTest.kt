package net.internetisalie.lunar.lang.editor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Drives the real Move Element Left/Right actions (Ctrl+Alt+Shift+←/→) over [LuaMoveLeftRightHandler]
 * for call args, table fields, generic-`for` names, and `local a, b` name lists (TC-03a..TC-03d), plus a
 * right-then-left round-trip. Real-flow DoD gate. EDITOR-07-03.
 */
class LuaMoveLeftRightHandlerTest : BasePlatformTestCase() {

    private fun moveRight() = myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_RIGHT)

    private fun moveLeft() = myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_LEFT)

    // TC-03a — reorder a call argument.
    fun testMoveCallArgumentRight() {
        myFixture.configureByText("a.lua", "f(a, <caret>b, c)")
        moveRight()
        myFixture.checkResult("f(a, c, b)")
    }

    // TC-03b — reorder a table-constructor field.
    fun testMoveTableFieldRight() {
        myFixture.configureByText("a.lua", "local t = {<caret>x, y, z}")
        moveRight()
        myFixture.checkResult("local t = {y, x, z}")
    }

    // TC-03c — reorder a generic-for name.
    fun testMoveGenericForNameRight() {
        myFixture.configureByText("a.lua", "for <caret>k, v in pairs(t) do end")
        moveRight()
        myFixture.checkResult("for v, k in pairs(t) do end")
    }

    // TC-03d — reorder a local name-list entry.
    fun testMoveLocalNameRight() {
        myFixture.configureByText("a.lua", "local <caret>a, b = 1, 2")
        moveRight()
        myFixture.checkResult("local b, a = 1, 2")
    }

    // Round-trip — right then left restores the original (mirrors GroovyMoveLeftRightHandlerTest).
    fun testRightThenLeftRoundTrips() {
        myFixture.configureByText("a.lua", "f(a, <caret>b, c)")
        moveRight()
        moveLeft()
        myFixture.checkResult("f(a, b, c)")
    }

    // A single-element list is a no-op (platform requires ≥2 movable siblings).
    fun testSingleArgumentIsNoOp() {
        myFixture.configureByText("a.lua", "f(<caret>a)")
        moveRight()
        myFixture.checkResult("f(a)")
    }
}
