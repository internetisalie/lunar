package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-09-P4-02 — benchmark gate (a timed unit test, NOT JMH).
 *
 * Asserts the parent §5 performance goals: ≤5-member unions are near-constant overhead, and ≤20
 * members stay within budget with graceful degradation.
 *
 * Methodology (parent design §3.2): wall-clock medians are not portable across CI hardware, so the
 * gate asserts *relative* budgets — each union size's median is compared to a 1-member baseline
 * (ratio bounds), with a loose absolute ceiling as a backstop. The bounds carry comfortable
 * headroom (the P0 spike `LuaUnionDistributionSpikeTest` saw ~400× headroom against a 50ms budget),
 * so this is a regression gate, not a flaky wall-clock assertion. A fresh graph is built per timed
 * iteration so the per-iteration memo never masks the distribution work.
 */
@RunWith(JUnit4::class)
class LuaUnionDistributionBenchmarkTest : BasePlatformTestCase() {

    private lateinit var anchor: PsiElement

    override fun setUp() {
        super.setUp()
        anchor = myFixture.addFileToProject("bench.lua", "")
    }

    /** A single-field exact table whose one field is a required (non-nil) Number — forces work. */
    private fun tableWith(graph: LuaTypeGraph, field: String): LuaGraphType.Table {
        val memberNode = graph.variable(anchor)
        memberNode.downSet.add(graph.use(anchor, LuaGraphType.Number))
        return LuaGraphType.Table(className = null, localMembers = mutableMapOf(field to memberNode), isExact = true)
    }

    /** A disjoint single-field-table union of [count] members, built in [graph]. */
    private fun union(graph: LuaTypeGraph, count: Int): LuaGraphType.Union =
        LuaGraphType.Union((1..count).map { tableWith(graph, "f$it") }.toSet())

    /**
     * Times one fresh compatibility run: a [count]-member union value checked against a single
     * compatible table use, so AND-distribution runs structurally over every member.
     */
    private fun timeOneRun(count: Int): Long {
        val graph = LuaTypeGraph()
        val value = union(graph, count)
        // A field-free, non-exact use accepts each member structurally (no required field), so the
        // AND-over-members distribution runs to completion and stays compatible.
        val use = LuaGraphType.Table(className = null, localMembers = mutableMapOf(), isExact = false)
        val valueNode = graph.value(anchor, value)
        val useNode = graph.use(anchor, use)
        val start = System.nanoTime()
        graph.addEdge(valueNode, useNode)
        return System.nanoTime() - start
    }

    private fun medianNanos(count: Int, iterations: Int): Long {
        val samples = LongArray(iterations) { timeOneRun(count) }
        return samples.sorted()[iterations / 2]
    }

    @Test
    fun testUnionDistributionStaysWithinRelativeBudget() {
        val iterations = 1000
        repeat(20) { // warm up the JIT across all three shapes
            timeOneRun(1)
            timeOneRun(5)
            timeOneRun(20)
        }

        val baselineNs = medianNanos(1, iterations)
        val fiveNs = medianNanos(5, iterations)
        val twentyNs = medianNanos(20, iterations)

        val baseline = baselineNs.coerceAtLeast(1L) // avoid divide-by-zero on a sub-ns baseline
        val fiveRatio = fiveNs.toDouble() / baseline
        val twentyRatio = twentyNs.toDouble() / baseline

        println("BENCH-09 baseline-median-ns=$baselineNs")
        println("BENCH-09 five-member-median-ns=$fiveNs ratio=$fiveRatio")
        println("BENCH-09 twenty-member-median-ns=$twentyNs ratio=$twentyRatio")

        // ≤5 members: near-constant overhead — within a generous constant factor of the baseline.
        assertTrue("5-member median ($fiveNs ns) must stay within 10× the baseline ($baselineNs ns)", fiveRatio < 10.0)
        // ≤20 members: graceful degradation — looser ratio plus an absolute 5ms ceiling backstop.
        assertTrue("20-member median ($twentyNs ns) must stay within 25× the baseline ($baselineNs ns)", twentyRatio < 25.0)
        assertTrue("20-member median must stay under the 5ms absolute ceiling (was $twentyNs ns)", twentyNs < 5_000_000L)
    }
}
