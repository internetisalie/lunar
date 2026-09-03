package net.internetisalie.lunar.refactoring.rename

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaUnusedLocalInspection
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-13-08 / NAV-13-05 — what rename and the unused-local inspection do once a colon call site
 * stops being a usage of a same-named declaration.
 *
 * Two ends of one binding, and driving one says nothing about the other, so both are here:
 *
 * - **At the call site** — `substituteElementToRename` reaches the existing
 *   `METHOD_FUNCTION → refuse` clause (`LuaRenameProcessor.kt:111-112`) instead of retargeting the
 *   same-named local. No change to the processor; what changed is what `resolvedDeclarationLeaf`
 *   returns.
 * - **At the same-named declaration** — the full `renameElementAtCaret` stops rewriting `t:m()`,
 *   which today is a half-applied rename of the [[BUG-457]] kind; and the declaration is reported
 *   unused where the colon member name was its only use.
 *
 * Covers `requirements.md` cases 23, 26, 28 and 33.
 */
@RunWith(JUnit4::class)
class LuaColonCallRenameRefusalTest : BasePlatformTestCase() {
    /**
     * Case 23 — rename **from the call site** is refused rather than retargeted.
     *
     * **Mutation** (`requirements.md` #1): delete the colon branch from
     * `LuaNameReference.multiResolve` — executed pre-change, `substituteElementToRename` returned
     * `LeafPsiElement@38 'm'`, the *local*, so a rename invoked at the method call would silently
     * have renamed a variable.
     */
    @Test
    fun renamingAColonCallSiteIsRefusedInsteadOfRetargetingASameNamedLocal() {
        myFixture.configureByText("test.lua", LOCAL_VARIABLE_FIXTURE)
        val callSite = leafAt(46)

        expectThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) {
            LuaRenameProcessor().substituteElementToRename(callSite, null)
        }
    }

    /**
     * Case 23's second half — the same fixture's local is now unused, because the colon member name
     * was its only use. `LOCAL_VARIABLE` is the kind; [theOtherDeclarationKindsAreAlsoReportedUnused]
     * covers the rest.
     *
     * **Mutation**: as above — no warning appears, because `t:m()` binds the local again.
     */
    @Test
    fun aLocalKeptAliveOnlyByAColonMemberNameIsReportedUnused() {
        myFixture.enableInspections(LuaUnusedLocalInspection())
        myFixture.configureByText("test.lua", LOCAL_VARIABLE_FIXTURE)

        assertEquals(
            "the local 'm' at 38 has no use once t:m() stops binding to it",
            listOf(38 to 39),
            rangesOf(UNUSED_LOCAL_M),
        )
    }

    /**
     * Case 28 — the withdrawal is not specific to `LOCAL_VARIABLE`. It reaches every kind
     * `LuaUnusedLocalInspection.classify` records that can carry a bare name: the generic-`for`
     * variable here, and the parameter in [aParameterUsedOnlyByAColonMemberNameIsReportedUnused].
     *
     * **Mutation**: as case 23's — the warning disappears.
     */
    @Test
    fun theOtherDeclarationKindsAreAlsoReportedUnused() {
        myFixture.enableInspections(LuaUnusedLocalInspection())
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nfunction t:m() end\nfor m in pairs(t) do t:m() end\n",
        )

        assertEquals(
            "the generic-for variable 'm' at 36 is used only by the colon member name",
            listOf(36 to 37),
            rangesOf(UNUSED_LOCAL_M),
        )
    }

    /**
     * Case 28 (b) — the `PARAMETER` kind. `checkParameters` defaults to `false` and has no settings
     * UI (`LuaUnusedLocalInspection.kt:36`), so the row is only reachable with it set.
     *
     * **Mutation**: as case 23's — no warning.
     */
    @Test
    fun aParameterUsedOnlyByAColonMemberNameIsReportedUnused() {
        val inspection = LuaUnusedLocalInspection()
        inspection.checkParameters = true
        myFixture.enableInspections(inspection)
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nfunction t:m() end\nlocal function f(m)\nt:m()\nend\nf(1)\n",
        )

        assertEquals(
            "the parameter 'm' at 49 is used only by the colon member name",
            listOf(49 to 50),
            rangesOf("Unused parameter 'm'"),
        )
    }

    /**
     * Case 26 (a) — the half case 23 does not cover: the full refactoring driven at the **same-named
     * declaration's** caret. The call is left alone.
     *
     * **Mutation**: as case 23's — the file becomes `t:RENAMED()`, a rename that rewrites a table
     * key the declaration does not own.
     */
    @Test
    fun renamingTheSameNamedLocalNoLongerRewritesTheColonCall() {
        myFixture.configureByText("test.lua", LOCAL_VARIABLE_FIXTURE)
        myFixture.editor.caretModel.moveToOffset(38)

        myFixture.renameElementAtCaret("RENAMED")

        assertEquals(
            "local t = {}\nfunction t:m() end\nlocal RENAMED = 1\nt:m()\n",
            myFixture.file.text,
        )
    }

    /**
     * Case 26 (b) — the sharper pin. Pre-change, renaming the **receiver** rewrote the *member name*
     * of the call while leaving the declaration `function m:m()` untouched, so the file no longer
     * agreed with itself — BUG-457's shape in a case BUG-457 did not cover.
     *
     * **Mutation**: as case 23's — the file becomes `RENAMED:RENAMED()`.
     */
    @Test
    fun renamingAReceiverThatSharesItsMembersNameRewritesOnlyTheReceiver() {
        myFixture.configureByText("test.lua", "local m = {}\nfunction m:m() end\nm:m()\n")
        myFixture.editor.caretModel.moveToOffset(6)

        myFixture.renameElementAtCaret("RENAMED")

        assertEquals(
            "local RENAMED = {}\nfunction m:m() end\nRENAMED:m()\n",
            myFixture.file.text,
        )
    }

    /**
     * Case 33 — a rename **conflict** is withdrawn together with the usage that raised it.
     * `LuaRenameConflictDetector`'s clause C1 scans the usage list `ReferencesSearch` produced, so a
     * site that stops being a usage stops being a capture candidate.
     *
     * **`print(m)` must stay outside the `do` block.** `distinctByAnchor`
     * (`LuaRenameConflictDetector.kt:359`) collapses collisions by capturing declaration, so a second
     * usage that also saw `n` would report one conflict on **both** sides and the mutation would stop
     * being reachable from this fixture.
     *
     * **Mutation**: as case 23's — `ConflictsInTestsException` is thrown, raised by the `t:m()` site.
     */
    @Test
    fun aConflictRaisedOnlyByTheColonCallSiteIsWithdrawnWithTheUsage() {
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nfunction t:m() end\nlocal m = 1\nprint(m)\ndo\nlocal n = 2\nt:m()\nend\n",
        )
        myFixture.editor.caretModel.moveToOffset(38)

        myFixture.renameElementAtCaret("n")

        assertEquals(
            "local t = {}\nfunction t:m() end\nlocal n = 1\nprint(n)\ndo\nlocal n = 2\nt:m()\nend\n",
            myFixture.file.text,
        )
    }

    /**
     * Anti-vacuity for [aConflictRaisedOnlyByTheColonCallSiteIsWithdrawnWithTheUsage]: the detector
     * is live on this fixture shape, so the case above is a withdrawn conflict rather than a
     * detector that never runs. Moving `print(m)` inside the `do` block makes it capture `n`.
     */
    @Test
    fun theConflictDetectorStillFiresWhenANonColonUsageIsCaptured() {
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nfunction t:m() end\nlocal m = 1\ndo\nlocal n = 2\nprint(m)\nend\n",
        )
        myFixture.editor.caretModel.moveToOffset(38)

        expectThrows(BaseRefactoringProcessor.ConflictsInTestsException::class.java) {
            myFixture.renameElementAtCaret("n")
        }
    }

    private fun expectThrows(
        expected: Class<out Throwable>,
        body: () -> Unit,
    ) {
        try {
            body()
        } catch (thrown: Throwable) {
            if (expected.isInstance(thrown)) return
            throw thrown
        }
        fail("expected ${expected.simpleName}, but nothing was thrown")
    }

    private fun leafAt(offset: Int): PsiElement =
        requireNotNull(myFixture.file.findElementAt(offset)) { "no leaf at $offset" }

    private fun rangesOf(description: String): List<Pair<Int, Int>> =
        myFixture
            .doHighlighting()
            .filter { info: HighlightInfo -> info.description == description }
            .map { it.startOffset to it.endOffset }
            .sortedBy { it.first }

    private companion object {
        const val LOCAL_VARIABLE_FIXTURE = "local t = {}\nfunction t:m() end\nlocal m = 1\nt:m()\n"
        const val UNUSED_LOCAL_M = "Unused local variable 'm'"
    }
}
