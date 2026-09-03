package net.internetisalie.lunar.lang.hierarchy

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-13-08 — Type Hierarchy at a colon call site declines instead of opening on a `---@class` local
 * that merely shares the member's name.
 *
 * `LuaTypeHierarchyProvider` is the second **receive**-half consumer: `elementAtCaret` reads
 * `CommonDataKeys.PSI_ELEMENT` first and only falls back to `findElementAt` (`:37-42`), so it
 * consumes this feature's output while naming no resolve API anywhere in its own source. It then
 * walks to a `LuaLocalVarDecl` carrying a class name.
 *
 * The withdrawal is correct for the same reason every other one is: `t:m()` names a table key on
 * `t`, never the class `m`. It is nonetheless a user-visible action that used to do something and
 * now does nothing, so `NAV-13-08` scopes it in.
 *
 * Covers `requirements.md` case 32.
 */
@RunWith(JUnit4::class)
class LuaColonCallTypeHierarchyTest : BasePlatformTestCase() {
    /**
     * The `---@class m` annotation is **load-bearing**: without it `LuaHierarchyUtil.className` is
     * null and `getTarget` is null on both sides, so this method could not fail.
     * [theHierarchyStillOpensOnTheClassLocalItself] is the control that proves the annotation is
     * live on this fixture.
     *
     * **Mutation** (`requirements.md` #1): delete the colon branch from
     * `LuaNameReference.multiResolve` — `getTarget` returns `LuaLocalVarDeclImpl@12 'local m = {}'`
     * and Type Hierarchy opens on the class `m`.
     */
    @Test
    fun typeHierarchyDeclinesAtAColonCallSite() {
        configureAt(59)

        assertNull(
            "the colon call site names a table key on t, never the class m",
            LuaTypeHierarchyProvider().getTarget(contextAtCaret()),
        )
    }

    /**
     * The control. At the `---@class` local's own name the hierarchy still opens, so the case above
     * is a withdrawal rather than a provider that never answers on this fixture.
     */
    @Test
    fun theHierarchyStillOpensOnTheClassLocalItself() {
        configureAt(18)

        assertNotNull(
            "Type Hierarchy must still open on the ---@class local itself",
            LuaTypeHierarchyProvider().getTarget(contextAtCaret()),
        )
    }

    private fun configureAt(offset: Int) {
        myFixture.configureByText(
            "test.lua",
            "---@class m\nlocal m = {}\nlocal t = {}\nfunction t:m() end\nt:m()\n",
        )
        myFixture.editor.caretModel.moveToOffset(offset)
    }

    /** Nothing is injected into the context — the point is what `CommonDataKeys.PSI_ELEMENT` yields. */
    private fun contextAtCaret(): DataContext =
        DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)
}
