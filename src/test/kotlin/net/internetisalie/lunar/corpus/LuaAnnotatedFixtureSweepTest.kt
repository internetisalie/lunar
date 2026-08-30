package net.internetisalie.lunar.corpus

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.LuaReturnTypeMismatchInspection
import net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection
import net.internetisalie.lunar.analysis.inspections.LuaDeprecatedApiInspection
import net.internetisalie.lunar.analysis.inspections.LuaGlobalCreationInspection
import net.internetisalie.lunar.analysis.inspections.LuaLanguageLevelInspection
import net.internetisalie.lunar.analysis.inspections.LuaShadowingVariableInspection
import net.internetisalie.lunar.analysis.inspections.LuaSuspiciousConcatenationInspection
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection
import net.internetisalie.lunar.analysis.inspections.LuaUnreachableCodeInspection
import net.internetisalie.lunar.analysis.inspections.LuaUnusedLocalInspection
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.RootAccessor

/**
 * BUG-473 DR-6 — the annotated fixture the corpus lane never had.
 *
 * Measured 2026-08-29: **0 of the 734 pinned corpus files carry any `---@` annotation**. The pinned
 * projects are pre-LuaCATS-era Lua, so the trigger condition for BUG-473 — one `---@class` making
 * `LuaTypesSnapshot.forFile` superlinear in call-site count — is unsatisfiable on 100 % of the
 * corpus, and a green sweep says nothing about the annotated path. This fixture is the smallest
 * thing that changes that. Real annotated projects are MAINT-39 step 3 and stay blocked until
 * BUG-473's growth exponent moves.
 *
 * **The gate is the root-resolution count, never a duration.** BUG-474 records the ratio assertion
 * in `LuaClassTagSnapshotPerformanceTest` reading 87× on one builder and 340× on another at the
 * same commit, because one side of that ratio is superlinear and the other linear. Walk-root counts
 * are a property of the fixture and the engine alone, so they are the same on every host.
 *
 * Opt-in with `-PwithCorpus` (`build.gradle.kts`), and deliberately **not** named `*Corpus*`: the
 * sweeps' ratchet requires that recording and verification share the invocation shape (BUG-418),
 * and anything inside the `--tests '*Corpus*'` filter shares their JVM and shifts their counts.
 */
class LuaAnnotatedFixtureSweepTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = System.getProperty("user.dir")

    override fun setUp() {
        super.setUp()
        // The same ten language-only inspections the project sweeps enable, so the fixture is
        // driven through the highlight path the sweep uses rather than a bare `forFile` call.
        myFixture.enableInspections(
            LuaUndeclaredVariableInspection(),
            LuaGlobalCreationInspection(),
            LuaUnusedLocalInspection(),
            LuaShadowingVariableInspection(),
            LuaDeprecatedApiInspection(),
            LuaSuspiciousConcatenationInspection(),
            LuaUnreachableCodeInspection(),
            LuaLanguageLevelInspection(),
            LuaTypeAssignabilityInspection(),
            LuaReturnTypeMismatchInspection(),
        )
    }

    /**
     * The BUG-473 gate. Measured on this fixture: `write` / `read` / `declaredDemand` re-derive a
     * walk root 2 028 / 343 / 133 times with the Phase 1 memo in place and 5 612 / 2 908 / 2 519
     * times without it, so a reversal clears every budget below by 1.9× to 10.1×. Counting is
     * deterministic given the fixture; nothing here is timed.
     */
    fun testAnnotatedFixtureStaysWithinItsRootResolutionBudget() {
        val types = LuaTypesSnapshot.forFile(highlighted(BUDGET_CARRIER)) as LuaTypesSnapshot
        // Every accessor is measured before anything is asserted: failing on the first one over
        // budget would leave the other two unproven, since a memo reversal moves all three.
        val exceeded = BUDGETS.mapNotNull { (accessor, budget) -> overBudget(types, accessor, budget) }
        assertTrue(
            "The BUG-473 root memo is not holding on $BUDGET_CARRIER:\n" + exceeded.joinToString("\n"),
            exceeded.isEmpty(),
        )
    }

    /**
     * The invariant the pinned sweeps assert over their trees, asserted over this one: annotated
     * Lua parses, and highlighting it throws nothing. BUG-390 overflowed the stack inside the type
     * engine on ordinary Lua; the annotated dialect had no equivalent guard because nothing in the
     * corpus reached it.
     */
    fun testAnnotatedFixtureParsesAndHighlightsWithoutFailing() {
        fixtureFiles().forEach { psiFile ->
            val parseErrors = runReadAction { PsiTreeUtil.findChildrenOfType(psiFile, PsiErrorElement::class.java) }
            assertEquals("${psiFile.name} did not parse cleanly: $parseErrors", 0, parseErrors.size)
            highlight(psiFile)
        }
    }

    /** The breach description when [accessor] is over [budget], else null. */
    private fun overBudget(
        types: LuaTypesSnapshot,
        accessor: RootAccessor,
        budget: Long,
    ): String? {
        val resolutions = types.rootResolutionCount(accessor)
        println("[annotated-fixture] $accessor rootResolutions=$resolutions budget=$budget")
        return "$accessor was re-derived at a walk root $resolutions times, over its budget of $budget"
            .takeIf { resolutions > budget }
    }

    private fun highlighted(fileName: String): PsiFile {
        val psiFile = fixtureFiles().single { it.name == fileName }
        highlight(psiFile)
        return psiFile
    }

    private fun highlight(psiFile: PsiFile) {
        val startedAt = System.nanoTime()
        myFixture.openFileInEditor(psiFile.virtualFile)
        val infos = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        println("[annotated-fixture] ${psiFile.name} highlightMs=$elapsedMs infos=${infos.size}")
    }

    /** Copied into the project the way the pinned sweeps copy theirs, so this is a VFS+index read. */
    private fun fixtureFiles(): List<PsiFile> {
        val copiedRoot = myFixture.copyDirectoryToProject(FIXTURE_DIR, "annotated")
        val psiManager = PsiManager.getInstance(myFixture.project)
        return runReadAction {
            luaFilesUnder(copiedRoot).sortedBy { it.name }.mapNotNull { psiManager.findFile(it) }
        }
    }

    private fun luaFilesUnder(root: VirtualFile): List<VirtualFile> {
        val luaFiles = mutableListOf<VirtualFile>()
        VfsUtilCore.iterateChildrenRecursively(root, null) { candidate ->
            if (!candidate.isDirectory && candidate.extension == "lua") luaFiles += candidate
            true
        }
        return luaFiles
    }

    private companion object {
        const val FIXTURE_DIR = "src/test/resources/corpus/annotated"

        /** `builder.lua` is the file whose call-site count was sized against the growth curve. */
        const val BUDGET_CARRIER = "builder.lua"

        /**
         * Measured on this fixture: 2 028 / 343 / 133 with the Phase 1 memo, 5 612 / 2 908 / 2 519
         * without it. Each budget sits 1.5×–1.9× above the memo-on count — close enough to fail on
         * a partial reversal, far enough that an unrelated engine change does not have to re-record
         * it — and 1.9×–10.1× below the memo-off count.
         */
        val BUDGETS =
            mapOf(
                RootAccessor.WRITE to 3_000L,
                RootAccessor.READ to 500L,
                RootAccessor.DECLARED_DEMAND to 250L,
            )
    }
}
