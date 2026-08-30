package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypePairWriteDifferential
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BUG-473 Phase 2 — the standing equivalence gate for the per-visit `write` hoist.
 *
 * The hoist resolves a value node's `write` once per outer-node visit instead of once per admitted
 * pair, which is what takes root `write` resolutions from quadratic to linear in call-site count.
 * It is **measured**-equivalent, not proven: within one visit an earlier pair can add an edge that
 * moves a later value node's `write`, and because the pair set is monotone that pair is never
 * re-examined. Nothing in the loop rules that out a priori.
 *
 * So the evidence is kept as a gate rather than run once and discarded. With
 * [LuaTypePairWriteDifferential] armed, every admitted pair also resolves `write` live and compares
 * it against the hoisted value the checker actually used; a change to the loop that makes the
 * hoisted value stale in a way that matters shows up here as a mismatch, on the same fixtures.
 *
 * A zero must not be allowed to pass vacuously — a harness that stopped observing reports 0
 * mismatches exactly as a correct one does, which is the failure DR-4 recorded. Two things rule
 * that out here. [theDifferentialReportsAMismatchWhenGivenOne] proves the instrument can produce a
 * non-zero answer at all, directly and independent of any fixture; [MINIMUM_COMPARISONS] then
 * proves it was actually fed the graph.
 */
class LuaTypePairWriteDifferentialTest : BaseDocumentTest() {
    @Test
    fun hoistedWriteMatchesTheLiveValueAtEveryAdmittedPair() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaTypePairWriteDifferential.start()
            try {
                differentialFixtures().forEach {
                    configureByText(it)
                    LuaTypesSnapshot.forFile(myFixture.file)
                }
            } finally {
                LuaTypePairWriteDifferential.stop()
            }

            val comparisons = LuaTypePairWriteDifferential.comparisons()
            assertTrue(
                comparisons >= MINIMUM_COMPARISONS,
                "the differential observed only $comparisons pairs, under the $MINIMUM_COMPARISONS " +
                    "these fixtures admit — a zero from this run would prove nothing",
            )
            assertEquals(
                0L,
                LuaTypePairWriteDifferential.mismatches(),
                "the per-visit write hoist diverged from the live value over $comparisons pairs; " +
                    "shapes: ${LuaTypePairWriteDifferential.mismatchShapes()}",
            )
        }
    }

    /**
     * The instrument's own mutation proof, run every time rather than performed once by hand: feed
     * [LuaTypePairWriteDifferential] two types that differ and confirm it says so. Without this the
     * zero above rests on the harness being wired up, which is the assumption DR-4 caught failing.
     */
    @Test
    fun theDifferentialReportsAMismatchWhenGivenOne() {
        LuaTypePairWriteDifferential.start()
        try {
            LuaTypePairWriteDifferential.compare(LuaGraphType.String, LuaGraphType.Number)
        } finally {
            LuaTypePairWriteDifferential.stop()
        }

        assertEquals(1L, LuaTypePairWriteDifferential.mismatches(), "the differential cannot report a mismatch")
        assertEquals(listOf("string -> number"), LuaTypePairWriteDifferential.mismatchShapes())
    }

    private fun differentialFixtures(): List<String> =
        listOf(
            paramViolationFixture(),
            annotatedFixture(20),
            annotatedFixture(40),
            REASSIGNED_UNION_FIXTURE,
            CALL_CHAIN_FIXTURE,
        )

    private fun paramViolationFixture(): String =
        """
        |---@class Builder
        |local Builder = {}
        |---@param n string
        |---@return Builder
        |function Builder:setName(n) return self end
        |local b = Builder
        |b:setName(42)
        """.trimMargin()

    private fun annotatedFixture(callSites: Int): String {
        val calls = (0 until callSites).joinToString("\n") { "b:setName(\"a$it\")" }
        return """
            |---@class Builder
            |local Builder = {}
            |function Builder:setName(n) end
            |
            |local b = Builder
            |$calls
            """.trimMargin()
    }

    private companion object {
        /**
         * Floor under the pairs these fixtures admit.
         *
         * Deliberately far under what they were measured to admit, because that count is **not**
         * stable across run contexts: the same fixtures produced 25 918 comparisons with this class
         * run alone and 5 644 inside the full suite. The mismatch count and the diagnostics these
         * fixtures emit were identical in both, so what varies is how much ambient stdlib/index
         * state the graphs pick up, not what the engine concludes. The floor's job is only to
         * separate "observed the graph" from "observed nothing", so it is set below both.
         */
        const val MINIMUM_COMPARISONS = 1_000L

        val REASSIGNED_UNION_FIXTURE =
            """
            |---@param s string
            |local function takesString(s) return s end
            |
            |local d = nil
            |if os.time() > 0 then d = "s" end
            |takesString(d)
            |
            |local t = {}
            |t.name = "a"
            |t.count = 1
            |takesString(t.count)
            """.trimMargin()

        val CALL_CHAIN_FIXTURE =
            """
            |---@class Node
            |---@field next Node
            |local Node = {}
            |
            |---@return Node
            |function Node:child() return self end
            |
            |local n = Node
            |local deep = n:child():child()
            |print(deep.next)
            """.trimMargin()
    }
}
