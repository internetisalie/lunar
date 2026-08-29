package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.RootAccessor
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * BUG-473 — the assertion that cannot be flaky.
 *
 * What regressed was not milliseconds, it was walk roots re-derived from scratch. On the
 * reproduction fixture — one `---@class` and 80 colon-call sites — the engine resolved
 * `write` / `read` / `declaredDemand` at a root 14 336 / 7 699 / 7 296 times, of which only
 * 7 211 / 647 / 325 were distinct `(node, graph-revision)` keys. Every duplicate re-walked the
 * whole reachable graph.
 *
 * Counting resolutions rather than timing them makes this deterministic given the fixture and
 * independent of the host, which is why it lives in the routine loop while the ratio assertion in
 * `LuaClassTagSnapshotPerformanceTest` stays behind `-PwithPerf`. A wall-clock budget is the
 * instrument that already failed to catch this defect.
 *
 * The bounds are the measured post-fix counts with headroom — 7 292 / 647 / 325 on this fixture,
 * where read and declaredDemand land on the predicted distinct-key counts exactly and write is 81
 * over them, the snapshot's own post-`checkTypes` reads. Losing the memo restores the pre-fix
 * figures, which clear every bound by 1.7× to 14.6×.
 */
class LuaTypeGraphRootResolutionBudgetTest : BaseDocumentTest() {
    @Test
    fun annotatedClassFixtureStaysWithinItsRootResolutionBudget() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            configureByText(annotatedCallSiteFixture())
            val types = LuaTypesSnapshot.forFile(myFixture.file) as LuaTypesSnapshot

            assertWithinBudget(types, RootAccessor.WRITE, WRITE_BUDGET)
            assertWithinBudget(types, RootAccessor.READ, READ_BUDGET)
            assertWithinBudget(types, RootAccessor.DECLARED_DEMAND, DECLARED_DEMAND_BUDGET)
        }
    }

    private fun assertWithinBudget(
        types: LuaTypesSnapshot,
        accessor: RootAccessor,
        budget: Long,
    ) {
        val resolutions = types.rootResolutionCount(accessor)
        assertTrue(
            resolutions <= budget,
            "$accessor was re-derived at a walk root $resolutions times, over the budget of $budget " +
                "for $CALL_SITE_COUNT call sites — the BUG-473 root memo is not holding",
        )
    }

    private fun annotatedCallSiteFixture(): String {
        val callSites = (0 until CALL_SITE_COUNT).joinToString("\n") { "b:setName(\"a$it\")" }
        return """
            |---@class Builder
            |local Builder = {}
            |function Builder:setName(n) end
            |
            |local b = Builder
            |$callSites
            """.trimMargin()
    }

    private companion object {
        const val CALL_SITE_COUNT = 80

        const val WRITE_BUDGET = 8_500L
        const val READ_BUDGET = 1_000L
        const val DECLARED_DEMAND_BUDGET = 500L
    }
}
