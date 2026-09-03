package net.internetisalie.lunar.lang.insight

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaMethodExpr
import net.internetisalie.lunar.lang.psi.LuaNameRef
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-13-02 / NAV-13-03: a colon-method declaration acquires a usage set.
 *
 * `ReferencesSearch.search(<colon declaration leaf>, allScope)` returned **0 in every receiver
 * shape** before this feature, the `---@class`-annotated one included (`risks-and-gaps.md` DR-01,
 * re-measured at `f1ac26cc`). `LuaNameReferenceSearcher` did scan colon call sites, but gates on
 * `isReferenceTo`, which resolves; a colon call site resolved to nothing, so the gate never passed.
 *
 * Neither `isReferenceTo` nor the searcher changed — `LuaColonCallResolution` returns the
 * declaration's IDENTIFIER **leaf**, which is exactly what the searcher normalises its target to, so
 * `isReferenceTo`'s first disjunct holds (design §3.7).
 *
 * Covers `requirements.md` cases 7 and 8, and the mirror assertion for each refused shape: a shape
 * that resolves to nothing must also contribute no usage, so no declaration acquires a usage the
 * call does not make.
 */
@RunWith(JUnit4::class)
class LuaColonCallFindUsagesTest : BasePlatformTestCase() {
    /**
     * Case 7 — the plain local table. Executed pre-change at `f1ac26cc`: `references=0` for this
     * fixture and for every other in DR-01 table 3. Falsifier: delete the colon branch from
     * `LuaNameReference.multiResolve`.
     */
    @Test
    fun aColonMethodDeclarationFindsItsCallSite() {
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nfunction t:m() end\nt:m()\n",
        )
        val declaration = leafAt(24)
        val references = ReferencesSearch.search(declaration, GlobalSearchScope.allScope(project)).findAll()

        assertEquals("expected exactly one reference to function t:m()", 1, references.size)
        val reference = references.first()
        val element = reference.element
        assertTrue(
            "the reference's element must be the call site's member name",
            element is LuaNameRef && element.parent is LuaMethodExpr,
        )
        assertEquals("the call site's offset", 34, element.textOffset)
        assertTrue("isReferenceTo must admit the declaration leaf", reference.isReferenceTo(declaration))
    }

    /**
     * Case 8 — two receivers carrying a same-named method: each declaration returns exactly its own
     * call site and neither returns the other's. This is a **pin against a future widening**, not
     * independent acceptance: no mutation makes the two receivers merge, because the lookup starts
     * from the receiver's own type node.
     */
    @Test
    fun twoReceiversWithASameNamedMethodDoNotShareUsages() {
        myFixture.configureByText(
            "test.lua",
            "local t = {}\n" +
                "function t:m() end\n" +
                "local u = {}\n" +
                "function u:m() end\n" +
                "t:m()\n" +
                "u:m()\n",
        )
        assertEquals("function t:m() must own only the t:m() site", listOf(66), referenceOffsetsTo(24))
        assertEquals("function u:m() must own only the u:m() site", listOf(72), referenceOffsetsTo(56))
    }

    /**
     * Case 9's shape, from the search side. The chain's second segment contributes no usage to
     * either `go` declaration — where resolving it against the receiver would hand `function A:go()`
     * a usage for a call whose real target is `function B:go()`. The first segment's declaration
     * does acquire its call site, so the fixture is not vacuously empty.
     */
    @Test
    fun aChainSecondSegmentContributesNoUsage() {
        myFixture.configureByText(
            "test.lua",
            "local A = {}\n" +
                "function A:go() end\n" +
                "local B = {}\n" +
                "function B:go() end\n" +
                "function A:next() return B end\n" +
                "A:next():go()\n",
        )
        assertEquals("function A:next() must own the first segment", listOf(99), referenceOffsetsTo(77))
        assertEmpty("function A:go() must not acquire the chain's second segment", referenceOffsetsTo(24))
        assertEmpty("function B:go() is unreachable from the chain", referenceOffsetsTo(57))
    }

    /**
     * Case 10's shape, from the search side. `a.b:m()` contributes no usage to the head's own
     * `function a:m()` — the graph anchors every suffix of a `var` on that `var`'s bare head, so
     * admitting it would attribute the call to the wrong method (TYPE-13 Gap 2.8).
     */
    @Test
    fun aSuffixedReceiverContributesNoUsage() {
        myFixture.configureByText(
            "test.lua",
            "local a = {}\n" +
                "a.b = {}\n" +
                "function a:m() end\n" +
                "function a.b:m() end\n" +
                "a.b:m()\n",
        )
        assertEmpty("the head's own method must not acquire the suffixed call", referenceOffsetsTo(33))
        assertEmpty("the suffixed receiver reaches no declaration at all", referenceOffsetsTo(54))
    }

    /**
     * The reach-pinned shapes of case 15, from the search side: each resolves to nothing, so each
     * contributes nothing. **No NAV-13-side falsifier exists for these** — they reach no declaration
     * because TYPE-13's `declarationOf` reports none (Gaps 2.7, 2.11), not because a NAV-13 clause
     * refuses them (`risks-and-gaps.md` DR-02 Finding 6). Kept as reach pins.
     */
    @Test
    fun anAliasedReceiverContributesNoUsage() {
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nfunction t:m() end\nlocal u = t\nu:m()\n",
        )
        assertEmpty("an aliased receiver reaches no declaration", referenceOffsetsTo(24))
    }

    /** Case 15's `self` receiver, from the search side. Reach pin; no NAV-13-side falsifier. */
    @Test
    fun aSelfReceiverContributesNoUsage() {
        myFixture.configureByText(
            "test.lua",
            "local C = {}\nfunction C:b() end\nfunction C:a() self:b() end\n",
        )
        assertEmpty("a self receiver reaches no declaration", referenceOffsetsTo(24))
    }

    /**
     * The cross-file **un-annotated** refusal. `LuaTypesSnapshot` is per file, so a global table's
     * methods are unreachable from another file (Gap 2.2) — executed pre-change, `references=0`, and
     * this feature does not change it. The annotated counterpart does cross, and is covered by
     * `LuaColonCallResolutionTest.annotatedReceiverResolvesAcrossFiles`.
     */
    @Test
    fun aCrossFileGlobalTableContributesNoUsage() {
        val declarer = myFixture.addFileToProject("decl.lua", "Obj = {}\nfunction Obj:m() end\n")
        myFixture.configureByText("use.lua", "Obj:m()\n")
        assertEmpty(
            "an un-annotated receiver cannot cross a file",
            referenceOffsetsIn(declarer, 22),
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun leafAt(offset: Int): PsiElement = leafIn(myFixture.file, offset)

    private fun leafIn(
        file: PsiFile,
        offset: Int,
    ): PsiElement = requireNotNull(file.findElementAt(offset)) { "no leaf at offset $offset in ${file.name}" }

    /** The offsets of every reference to the declaration leaf at [declarationOffset], sorted. */
    private fun referenceOffsetsTo(declarationOffset: Int): List<Int> =
        referenceOffsetsIn(myFixture.file, declarationOffset)

    private fun referenceOffsetsIn(
        file: PsiFile,
        declarationOffset: Int,
    ): List<Int> =
        ReferencesSearch
            .search(leafIn(file, declarationOffset), GlobalSearchScope.allScope(project))
            .findAll()
            .map { it.element.textOffset }
            .sorted()
}
