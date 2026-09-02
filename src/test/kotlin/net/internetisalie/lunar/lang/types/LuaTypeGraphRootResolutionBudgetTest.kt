package net.internetisalie.lunar.lang.types

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaUnionType
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

    /**
     * TYPE-13 Phase 4 — `annotatedClassFixtureStaysWithinItsRootResolutionBudget` above stops at
     * `LuaTypesSnapshot.forFile`; it never calls `graphTypeToLuaType`, so it cannot see the walk
     * `LuaMemberDeclarations.declaringNodeOf` performs when a member is populated
     * (`TYPE-13`'s design §4.3 `putGraphMember`). This method converts the last call site's
     * receiver — `b:setName("a79")` — and re-asserts the write/read budgets against that path.
     *
     * Measured on the 80-call-site fixture at commit time: `WRITE` = 574, `READ` = 648 — 2 and 1
     * over the pre-conversion walk (572/647). Budgets below carry ~8% headroom over that
     * measurement.
     *
     * **Mutation finding (`requirements.md` case 15), executed and reverted:** the prescribed
     * mutation — have `declaringNodeOf` read `node.write` at each step instead of relying on
     * `declaresMember` to short-circuit — does **not** redden this test, on this fixture, under
     * three variants tried (read-then-shortcut; unconditional-read-with-shortcut-removed;
     * unconditional-read walking the full `upSet` fan-out to the 64-node cap). All three reproduced
     * `WRITE` = 574 exactly. Root cause, confirmed with a temporary visited-count probe: `setName`'s
     * member node (`Table.localMembers["setName"]`) is minted directly at the declaration site with
     * `declaresMember = true`, so the correct code already returns it at step 0 without touching
     * `upSet` at all on this fixture — and every node reachable from it via `upSet` (the BFS
     * traversed 64 of them before hitting `MAX_VISITED`) already has its `write` resolved in the
     * `RootMemo` from the ordinary type-check pass that `LuaTypesSnapshot.forFile` performs before
     * this method ever calls `graphTypeToLuaType`. A duplicate read of an already-memoized
     * `(node, graph-revision)` key is free by the same mechanism BUG-473 relies on, so this
     * particular mutation is invisible to this counter on this fixture regardless of how
     * aggressively `declaringNodeOf` re-reads `write` — not because the budget carries slack, but
     * because the read it adds can never be a cache miss here. A fixture whose `declaresMember`
     * member sits one or more `upSet` hops away from nodes the initial pass never visits would be
     * needed to exercise this mutation; none of the existing TYPE-13 fixtures has that shape.
     */
    @Test
    fun conversionPathStaysWithinItsRootResolutionBudget() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            configureByText(annotatedCallSiteFixture())
            val types = LuaTypesSnapshot.forFile(myFixture.file) as LuaTypesSnapshot

            val receiverRef =
                PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java).last { it.text == "b" }
            val converted = types.graphTypeToLuaType(types.getValueType(receiverRef))
            assertTrue((converted as LuaUnionType).types.any { it.resolveMember("setName") != null })

            assertWithinBudget(types, RootAccessor.WRITE, CONVERSION_WRITE_BUDGET)
            assertWithinBudget(types, RootAccessor.READ, CONVERSION_READ_BUDGET)
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

        // TYPE-13 Phase 4 — measured, see conversionPathStaysWithinItsRootResolutionBudget's KDoc.
        const val CONVERSION_WRITE_BUDGET = 620L
        const val CONVERSION_READ_BUDGET = 700L
    }
}
