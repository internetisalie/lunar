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
 * The bounds are the measured counts with headroom — 572 / 647 / 325 on this fixture.
 *
 * `write` was re-baselined from 8 500 to 600 by BUG-473 Phase 2. Phase 1's memo left it at 7 292
 * here, quadratic in call-site count, because `checkTypes` bumps the graph revision tens of
 * thousands of times mid-loop and invalidates the memo it would otherwise hit; Phase 2 resolves a
 * value node's `write` once per outer-node visit instead of once per admitted pair, which makes the
 * count linear (7n + 12 — 152 / 292 / 572 at n = 20 / 40 / 80). Leaving the budget at 8 500 after
 * that would have made it assert nothing: reverting the hoist restores 7 292, 12.7× over the bound.
 *
 * DR-8 predicted 653 here (8n + 13). That figure is the **eager** variant it measured, which reads
 * every up-set value node at the head of each visit; the shipped form fills its per-visit slots
 * lazily on first need and so resolves 81 fewer roots on this fixture.
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

        const val WRITE_BUDGET = 600L
        const val READ_BUDGET = 1_000L
        const val DECLARED_DEMAND_BUDGET = 500L
    }
}
