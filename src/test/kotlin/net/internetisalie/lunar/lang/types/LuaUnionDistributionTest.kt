package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-09-P2 — distributive compatibility safety limits (breadth/depth) and per-run memoization.
 *
 * The OR/AND distribution itself shipped in P1; these tests pin the P2 additions:
 *  - TC-TYPE-09-P2-01/02 regressions for OR success / AND failure.
 *  - TC-TYPE-09-P2-03 depth-limit termination that ASSUMES compatibility (returns true), per
 *    the reconciled requirement / TYPE-DR-04 (no false-positive error).
 *  - breadth >100 head-match fallback.
 *  - memo isolation across distinct generic instantiations.
 */
@RunWith(JUnit4::class)
class LuaUnionDistributionTest : BasePlatformTestCase() {

    private lateinit var anchor: PsiElement

    override fun setUp() {
        super.setUp()
        anchor = myFixture.addFileToProject("dist.lua", "")
    }

    /** A single-field exact table whose one field is a required (non-nil) Number. */
    private fun tableWith(graph: LuaTypeGraph, field: String): LuaGraphType.Table {
        val memberNode = graph.variable(anchor)
        memberNode.downSet.add(graph.use(anchor, LuaGraphType.Number))
        return LuaGraphType.Table(className = null, localMembers = mutableMapOf(field to memberNode), isExact = true)
    }

    private fun union(graph: LuaTypeGraph, prefix: String, count: Int): LuaGraphType.Union =
        LuaGraphType.Union((1..count).map { tableWith(graph, "$prefix$it") }.toSet())

    /** Drives the production ValueNode -> UseNode compatibility path and returns emitted errors. */
    private fun check(graph: LuaTypeGraph, value: LuaGraphType, use: LuaGraphType): List<String> {
        graph.addEdge(graph.value(anchor, value), graph.use(anchor, use))
        return graph.errors.map { it.message }
    }

    // -- TC-TYPE-09-P2-01: OR success (regression) ---------------------------------------------

    @Test
    fun testOrDistributionSucceeds() {
        val file = myFixture.configureByText(
            "or.lua",
            """
            ---@type string | number
            local x = 42 -- OK: number is a member of string | number
            """.trimIndent(),
        )
        val errors = LuaTypesSnapshot.forFile(file).getErrors()
        assertTrue("number into string|number must be compatible, got: ${errors.map { it.message }}", errors.isEmpty())
    }

    // -- TC-TYPE-09-P2-02: AND failure (regression) --------------------------------------------

    @Test
    fun testAndDistributionFails() {
        val file = myFixture.configureByText(
            "and.lua",
            """
            ---@type string | number
            local x = 42

            ---@type string
            local s = x -- Error: number (via union) violates string-only use
            """.trimIndent(),
        )
        val errors = LuaTypesSnapshot.forFile(file).getErrors()
        assertFalse("string|number into string must error", errors.isEmpty())
        assertTrue(
            "Error should mention number and string, got: ${errors.map { it.message }}",
            errors.any { it.message.contains("number") && it.message.contains("string") },
        )
    }

    // -- TC-TYPE-09-P2-03: depth limit -> terminates, assumes compatibility --------------------

    @Test
    fun testDepthLimitAssumesCompatibleWithoutStackOverflow() {
        val graph = LuaTypeGraph()
        // Nest disjoint unions far beyond depth 10 so distribution recursion must hit the cutoff.
        var deep: LuaGraphType = union(graph, "D", 3)
        repeat(40) { deep = LuaGraphType.Union(setOf(deep, tableWith(graph, "extra$it"))) }

        var errors: List<String> = emptyList()
        // Asserting no StackOverflowError AND no false-positive error (depth cutoff returns true).
        errors = check(graph, deep, deep)

        assertTrue("Deep distribution must assume compatibility (no false-positive error), got: $errors", errors.isEmpty())
    }

    // -- Breadth fallback: >100 members -> shallowHeadMatch, completes & compatible ------------

    @Test
    fun testBreadthFallbackCompletesCompatible() {
        val graph = LuaTypeGraph()
        // 150 distinct field-free exact tables -> a >MAX_UNION_BREADTH union. Field-free so the
        // existing structural-propagation pass cannot emit a missing-field error and confound this.
        val wide = LuaGraphType.Union(
            (1..150).map { LuaGraphType.Table(className = "W$it", localMembers = mutableMapOf(), isExact = false) }.toSet(),
        )
        val emptyUse = LuaGraphType.Table(className = null, localMembers = mutableMapOf(), isExact = false)
        // value is the over-breadth union -> isCompatible takes the breadth fallback (shallowHeadMatch:
        // Table heads overlap -> compatible). Must terminate and emit no assignability error.
        val errors = check(graph, wide, emptyUse)
        assertTrue("Over-breadth union vs head-compatible target must complete without error, got: $errors", errors.isEmpty())
    }

    // -- Memo: distinct generic instantiations do not collide ----------------------------------

    @Test
    fun testGenericInstantiationsDoNotCollideUnderMemo() {
        // Two distinct call sites of the same generic template must yield independent results:
        // f(42) is number, f(true) is boolean; only the boolean->number assignment errors.
        val file = myFixture.configureByText(
            "memo.lua",
            """
            ---@generic T
            ---@param x T
            ---@return T
            local function f(x) return x end

            local a = f(42)
            ---@type number
            local a_check = a -- OK

            local b = f(true)
            ---@type number
            local b_err = b -- Error: boolean not assignable to number
            """.trimIndent(),
        )
        val errors = LuaTypesSnapshot.forFile(file).getErrors()
        assertTrue(
            "Distinct instantiations must not collide; expected a boolean/number error, got: ${errors.map { it.message }}",
            errors.any { it.message.contains("boolean") && it.message.contains("number") },
        )
        assertTrue(
            "The number call site 'a' must remain error-free under memoization, got: ${errors.map { it.message }}",
            errors.none { it.element.text == "a" },
        )
    }

    // -- Memo: re-checking an identical pair is served from cache -------------------------------

    @Test
    fun testIdenticalPairServedFromMemo() {
        val graph = LuaTypeGraph()
        // value=Union -> checkCompatibility drives isCompatible(union, Number, ctx), which memoizes
        // each genuine (member, Number) structural result. (addEdge ValueNode->UseNode runs the
        // compatibility check eagerly, so the memo is populated without a checkTypes pass.)
        val value = union(graph, "M", 5)
        graph.addEdge(graph.value(anchor, value), graph.use(anchor, LuaGraphType.Number))
        val sizeAfterFirst = graph.compatMemoSize()
        assertTrue("Genuine structural results must populate the memo", sizeAfterFirst > 0)

        // Re-checking the identical pair must be served from the cache: no new keys appear.
        graph.addEdge(graph.value(anchor, value), graph.use(anchor, LuaGraphType.Number))
        assertEquals("Identical pair must be served from memo (no new keys)", sizeAfterFirst, graph.compatMemoSize())

        // A fresh checkTypes() snapshot clears the per-run memo.
        graph.checkTypes()
        // After clear, checkTypes only re-checks Variable up/down sets (none here), so the memo
        // is reset; the per-run lifetime mirrors checkedPairs.
        assertEquals("checkTypes() must clear the per-run memo", 0, graph.compatMemoSize())
    }
}
