package net.internetisalie.lunar.analysis.inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-13-08 — `LuaDeprecatedApiInspection` moves in **both** directions when a colon call site stops
 * binding lexically, and both directions are in scope (`design.md` §7, "`LuaDeprecatedApiInspection`
 * moves in both directions").
 *
 * The inspection reaches every colon member name because its `isDeclaration` gate lists
 * `LuaLocalFuncDecl` / `LuaAttName` / `LuaFuncName` / `LuaNameList` and has no `LuaMethodExpr`
 * branch, so the name falls through to `multiResolve`. `getDeprecatedTag` then walks
 * `PsiTreeUtil.getParentOfType(resolved, LuaCatsCommentOwner)` on whatever leaf comes back — which
 * is why changing the leaf changes the warning.
 *
 * Each method asserts the **exact offsets** `requirements.md` names rather than a count: a count
 * cannot tell a withdrawn warning from a warning that moved.
 *
 * Covers `requirements.md` cases 21 and 22.
 */
@RunWith(JUnit4::class)
class LuaColonCallDeprecationTest : BasePlatformTestCase() {
    /**
     * Case 21 — **direction 1, withdrawn**, and the withdrawal is the correct answer. `t:m()` does
     * not call the deprecated local function `m`, so the warning it used to carry asserted a call
     * that does not exist.
     *
     * The surviving warning is load-bearing as a control: `99..100` is the plain call `m()`, which
     * never went through this feature. The method declaration's own name `85..86` carried one until
     * [[BUG-476]]: a function-name segment after the head resolved lexically, to the deprecated
     * `local function m` `@53`, although `function t:m()` does not call it.
     *
     * **Mutation** (`requirements.md` #1): delete the colon branch from
     * `LuaNameReference.multiResolve` — a third warning appears at `95..96`, because `resolve()` on
     * `m@95` reverts from the method's name leaf `@85` to the deprecated local function `@53`.
     */
    @Test
    fun aCallSiteWarningNamingALexicalBindingIsWithdrawn() {
        myFixture.enableInspections(LuaDeprecatedApiInspection())
        myFixture.configureByText(
            "test.lua",
            "---@deprecated Use the method instead\n" +
                "local function m() end\n" +
                "local t = {}\n" +
                "function t:m() end\n" +
                "t:m()\n" +
                "m()\n",
        )

        assertEquals(
            "the t:m() call site at 95 must carry no deprecation warning",
            listOf(99 to 100),
            rangesOf("Deprecated API: Use the method instead"),
        )
    }

    /**
     * Case 22 (a) — **direction 2 does NOT fire when the receiver's name differs from the member's**,
     * and that is the inspection's guard rather than this feature's branch.
     *
     * `getDeprecatedTag`'s `LuaFuncDecl` guard compares the resolved leaf against
     * `commentOwner.funcName.nameRef.identifier`, and for `function t:m()` that accessor yields the
     * **receiver** `t`@42, not the method `m`. Executed: `textEq=false`, so the tag is dropped.
     *
     * **Mutation** — *not* this feature's: delete the `LuaFuncDecl` name-equality guard at
     * `LuaDeprecatedApiInspection.kt:81-86`. This method then gains a call-site warning at `54..55`.
     */
    @Test
    fun aDeprecatedMethodRaisesNoWarningWhenTheReceiverNameDiffers() {
        myFixture.enableInspections(LuaDeprecatedApiInspection())
        myFixture.configureByText(
            "test.lua",
            "local t = {}\n---@deprecated gone\nfunction t:m() end\nt:m()\n",
        )

        assertEquals(
            "the receiver 't' does not equal the member 'm', so the inspection's own guard drops the tag",
            emptyList<Pair<Int, Int>>(),
            rangesOf(DEPRECATED_GONE),
        )
    }

    /**
     * Case 22 (b) — the control that proves the **guard**, not the branch, is what silences (a).
     * Spelling both names `m` makes `textEq=true`, and the same fixture shape then warns at the call
     * site. The declaration's own method name `44..45` warned too until [[BUG-476]], because it
     * resolved to its own receiver `m@42` and so reached the deprecated declaration; a segment after
     * the head of a function name no longer resolves lexically, so only the call site warns.
     *
     * **Mutation** (`requirements.md` #1): delete the colon branch — `54..55` disappears.
     */
    @Test
    fun aDeprecatedMethodWarnsAtTheCallSiteWhenReceiverAndMemberShareAName() {
        myFixture.enableInspections(LuaDeprecatedApiInspection())
        myFixture.configureByText(
            "test.lua",
            "local m = {}\n---@deprecated gone\nfunction m:m() end\nm:m()\n",
        )

        assertEquals(
            "the call site at 54 warns once the receiver's name equals the member's",
            listOf(54 to 55),
            rangesOf(DEPRECATED_GONE),
        )
    }

    private fun rangesOf(description: String): List<Pair<Int, Int>> =
        myFixture
            .doHighlighting()
            .filter { info: HighlightInfo -> info.description == description }
            .map { it.startOffset to it.endOffset }
            .sortedBy { it.first }

    private companion object {
        const val DEPRECATED_GONE = "Deprecated API: gone"
    }
}
