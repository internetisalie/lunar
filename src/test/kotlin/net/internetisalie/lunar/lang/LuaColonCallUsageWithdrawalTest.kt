package net.internetisalie.lunar.lang

import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageInfo
import net.internetisalie.lunar.refactoring.LuaSafeDeleteProcessor
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-13-08 / NAV-13-03 — the **mirror** of `LuaColonCallFindUsagesTest`: what the same-named
 * declaration the call site used to bind to *loses*, driven from that declaration's own end.
 *
 * An element-taking API has two ends and driving one says nothing about the other. Find Usages on
 * such a declaration returns one fewer usage, in every kind `LuaDeclarationSite` classifies that can
 * carry a bare name — so these cases cover `LOCAL_VARIABLE`, `LOCAL_FUNCTION` and `GLOBAL_FUNCTION`
 * rather than generalising from one.
 *
 * Covers `requirements.md` cases 25, 27 and 29.
 */
@RunWith(JUnit4::class)
class LuaColonCallUsageWithdrawalTest : BasePlatformTestCase() {
    /**
     * Case 25 (a) — `LOCAL_VARIABLE`. The colon call site was the local's only usage, so its set is
     * now empty.
     *
     * **Mutation** (`requirements.md` #1): delete the colon branch — the search returns `[46]`.
     */
    @Test
    fun aSameNamedLocalVariableLosesTheColonCallSite() {
        myFixture.configureByText("test.lua", "local t = {}\nfunction t:m() end\nlocal m = 1\nt:m()\n")

        assertEquals("the local 'm' owns no usage", emptyList<Int>(), usageOffsetsAt(38))
    }

    /**
     * Case 25 (b) — `LOCAL_FUNCTION`, and the row that shows the withdrawal is *surgical*. Offset 47
     * is the `function t:m()` declaration's own name, which keeps its lexical resolution because its
     * parent is a `LuaFuncNameMethod` rather than a `LuaMethodExpr`; 61 is the plain call `m()`.
     * Only 57 — the colon call site — leaves.
     *
     * **Mutation**: as (a) — the search returns `[47, 57, 61]`.
     */
    @Test
    fun aSameNamedLocalFunctionLosesOnlyTheColonCallSite() {
        myFixture.configureByText(
            "test.lua",
            "local function m() end\nlocal t = {}\nfunction t:m() end\nt:m()\nm()\n",
        )

        assertEquals("only the colon call site leaves", listOf(47, 61), usageOffsetsAt(15))
        assertEquals("and the method declaration gains it", listOf(57), usageOffsetsAt(47))
    }

    /**
     * Case 25 (c) — `GLOBAL_FUNCTION`. Nothing about the withdrawal is specific to a file-local kind.
     *
     * **Mutation**: as (a) — the search returns `[41, 51]`.
     */
    @Test
    fun aSameNamedGlobalFunctionLosesTheColonCallSite() {
        myFixture.configureByText(
            "test.lua",
            "function m() end\nlocal t = {}\nfunction t:m() end\nt:m()\n",
        )

        assertEquals("only the method declaration's own name remains", listOf(41), usageOffsetsAt(9))
        assertEquals("and the method declaration gains the call site", listOf(51), usageOffsetsAt(41))
    }

    /**
     * Case 27 — the transfer reaches the **refactoring**, not only the search API. Safe Delete
     * searches `ReferencesSearch` on the leaf's `useScope` (`LuaSafeDeleteProcessor.kt:89`), so it
     * stops blocking on a site that is no longer a usage.
     *
     * **Mutation**: as (a) — the two swap, `m@38` yielding `[46]` and `m@24` yielding `[]`.
     */
    @Test
    fun safeDeleteFollowsTheTransferAtBothEnds() {
        myFixture.configureByText("test.lua", "local t = {}\nfunction t:m() end\nlocal m = 1\nt:m()\n")

        assertEquals("the local no longer blocks Safe Delete", emptyList<Int>(), safeDeleteOffsetsAt(38))
        assertEquals("the method declaration does", listOf(46), safeDeleteOffsetsAt(24))
    }

    /**
     * Case 29 — the withdrawal with **no counterpart gained**. `t:zz()` names a key the receiver's
     * type does not carry, so the usage leaves `zz`'s set and joins nobody's and `zz` becomes unused.
     * Every other row here is a transfer, and a reader who saw only those would take the transfer for
     * the rule.
     *
     * **Mutation**: as (a) — the search returns `[28]` and `resolve()` returns `LeafPsiElement@19`.
     */
    @Test
    fun anUnresolvableMemberWithdrawsTheUsageAndGainsNothing() {
        myFixture.configureByText("test.lua", "local t = {}\nlocal zz = 1\nt:zz()\n")

        assertEquals("the local 'zz' loses its only usage", emptyList<Int>(), usageOffsetsAt(19))
        assertNull(
            "and the call site resolves to nothing rather than to the local",
            leafAt(28).parent.reference?.resolve(),
        )
    }

    private fun usageOffsetsAt(offset: Int): List<Int> =
        ReferencesSearch
            .search(leafAt(offset), GlobalSearchScope.allScope(project))
            .findAll()
            .map { it.element.textOffset }
            .sorted()

    private fun safeDeleteOffsetsAt(offset: Int): List<Int> {
        val leaf = leafAt(offset)
        val usages = mutableListOf<UsageInfo>()
        LuaSafeDeleteProcessor().findUsages(leaf, arrayOf(leaf), usages)
        return usages.mapNotNull { it.element?.textOffset }.sorted()
    }

    private fun leafAt(offset: Int): PsiElement =
        requireNotNull(myFixture.file.findElementAt(offset)) { "no leaf at $offset" }
}
