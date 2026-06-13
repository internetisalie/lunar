package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-09-P0-01 — distribution cost bound (THROWAWAY de-risking spike).
 *
 * Question: do the breadth-100 / depth-10 limits (parent design.md §2.3.1) keep distribution
 * tractable, and does the engine blow up without them?
 *
 * Method: build disjoint single-field tables A1|…|A100 vs B1|…|B100, drive a real pairwise
 * structural check through a throwaway recursive `compat(value, use, depth)` copy of the
 * distribution rule, timed with and without limits. We also time the production
 * `addEdge(value, use)` path for reference. Numbers are recorded in results/union-perf.md.
 */
@RunWith(JUnit4::class)
class LuaUnionDistributionSpikeTest : BasePlatformTestCase() {

    private lateinit var anchor: PsiElement
    private lateinit var graph: LuaTypeGraph

    override fun setUp() {
        super.setUp()
        anchor = myFixture.addFileToProject("spike.lua", "")
        graph = LuaTypeGraph()
    }

    /** A single-field exact table whose one field [field] is a required (non-nil) Number. */
    private fun tableWith(field: String): LuaGraphType.Table {
        val memberNode = graph.variable(anchor)
        // A non-nil read on the member makes it a *required* field, forcing structural work.
        memberNode.downSet.add(graph.use(anchor, LuaGraphType.Number))
        return LuaGraphType.Table(
            className = null,
            localMembers = mutableMapOf(field to memberNode),
            isExact = true,
        )
    }

    private fun disjointUnion(prefix: String, count: Int): LuaGraphType.Union =
        LuaGraphType.Union((1..count).map { tableWith("$prefix$it") }.toSet())

    // -- Throwaway distribution copies ---------------------------------------------------------

    /** Unbounded copy of the distribution rule (mirrors LuaTypeGraph.isCompatible structure). */
    private fun compatUnbounded(
        value: LuaGraphType,
        use: LuaGraphType,
        visited: MutableSet<Pair<LuaGraphType, LuaGraphType>>,
    ): Boolean {
        if (value == use) return true
        if (!visited.add(value to use)) return true
        return when {
            value is LuaGraphType.Union -> value.types.all { compatUnbounded(it, use, visited) }
            use is LuaGraphType.Union -> use.types.any { compatUnbounded(value, it, visited) }
            value is LuaGraphType.Table && use is LuaGraphType.Table -> structural(value, use) { v, u -> compatUnbounded(v, u, visited) }
            else -> false
        }
    }

    /** Bounded copy: breadth>100 -> shallow head-match fallback; depth>10 -> false. */
    private fun compatBounded(
        value: LuaGraphType,
        use: LuaGraphType,
        depth: Int,
        visited: MutableSet<Pair<LuaGraphType, LuaGraphType>>,
    ): Boolean {
        if (value == use) return true
        if (depth > 10) return false
        if (breadthOf(value) > 100 || breadthOf(use) > 100) return headMatch(value, use)
        if (!visited.add(value to use)) return true
        return when {
            value is LuaGraphType.Union -> value.types.all { compatBounded(it, use, depth + 1, visited) }
            use is LuaGraphType.Union -> use.types.any { compatBounded(value, it, depth + 1, visited) }
            value is LuaGraphType.Table && use is LuaGraphType.Table ->
                structural(value, use) { v, u -> compatBounded(v, u, depth + 1, visited) }
            else -> false
        }
    }

    private fun breadthOf(t: LuaGraphType): Int = if (t is LuaGraphType.Union) t.types.size else 1

    private fun headMatch(value: LuaGraphType, use: LuaGraphType): Boolean =
        value::class == use::class

    private inline fun structural(
        value: LuaGraphType.Table,
        use: LuaGraphType.Table,
        rec: (LuaGraphType, LuaGraphType) -> Boolean,
    ): Boolean {
        for ((key, useNode) in use.getMembers()) {
            val valueNode = value.getMembers()[key] ?: return false
            if (!rec(valueNode.write, useNode.read)) return false
        }
        return true
    }

    private fun median(samplesNanos: LongArray): Double {
        val sorted = samplesNanos.sorted()
        return sorted[sorted.size / 2] / 1_000_000.0
    }

    @Test
    fun testDistributionCostBound() {
        val unionA = disjointUnion("A", 100)
        val unionB = disjointUnion("B", 100)
        val iterations = 100

        // Warm up the JIT.
        repeat(20) {
            compatBounded(unionA, unionB, 0, mutableSetOf())
            compatUnbounded(unionA, unionB, mutableSetOf())
        }

        val boundedSamples = LongArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            compatBounded(unionA, unionB, 0, mutableSetOf())
            boundedSamples[i] = System.nanoTime() - start
        }

        val unboundedSamples = LongArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            compatUnbounded(unionA, unionB, mutableSetOf())
            unboundedSamples[i] = System.nanoTime() - start
        }

        // Production path through addEdge (uses the engine's own visited guard).
        val prodSamples = LongArray(iterations)
        for (i in 0 until iterations) {
            val g = LuaTypeGraph()
            val a = disjointUnionFor(g, "A", 100)
            val b = disjointUnionFor(g, "B", 100)
            val v = g.value(anchor, a)
            val u = g.use(anchor, b)
            val start = System.nanoTime()
            g.addEdge(v, u)
            prodSamples[i] = System.nanoTime() - start
        }

        val boundedMs = median(boundedSamples)
        val unboundedMs = median(unboundedSamples)
        val prodMs = median(prodSamples)

        // Deep / nested-union shape to probe depth cost: nest unions 12 levels (exceeds depth 10).
        var deep: LuaGraphType = disjointUnion("D", 3)
        repeat(12) { deep = LuaGraphType.Union(setOf(deep, tableWith("extra$it"))) }
        val deepStart = System.nanoTime()
        val deepResult = compatBounded(deep, deep, 0, mutableSetOf())
        val deepMs = (System.nanoTime() - deepStart) / 1_000_000.0

        // Pathological "wide tables nested deep" shape: the visited (value,use) guard collapses
        // only *repeated* pairs; nesting disjoint unions inside table fields explores a product of
        // *distinct* pairs, which is where the unbounded recursion can actually blow up.
        val pathoValue = nestedWideUnion("PV", width = 8, depth = 14)
        val pathoUse = nestedWideUnion("PU", width = 8, depth = 14)

        val boundedPathoStart = System.nanoTime()
        compatBounded(pathoValue, pathoUse, 0, mutableSetOf())
        val boundedPathoMs = (System.nanoTime() - boundedPathoStart) / 1_000_000.0

        val unboundedPathoMs = timedOrTimeout(5_000) {
            compatUnbounded(pathoValue, pathoUse, mutableSetOf())
        }

        println("SPIKE-01 bounded-median-ms=$boundedMs")
        println("SPIKE-01 unbounded-median-ms=$unboundedMs")
        println("SPIKE-01 production-addEdge-median-ms=$prodMs")
        println("SPIKE-01 deep-nested-ms=$deepMs deep-result=$deepResult")
        println("SPIKE-01 ratio-unbounded-over-bounded-100x100=${unboundedMs / maxOf(boundedMs, 1e-6)}")
        println("SPIKE-01 patho-bounded-ms=$boundedPathoMs")
        println("SPIKE-01 patho-unbounded-ms=${unboundedPathoMs ?: ">5000 (timed out)"}")
        println(
            "SPIKE-01 patho-ratio=" +
                if (unboundedPathoMs == null) "INF (unbounded non-terminating within 5s)"
                else (unboundedPathoMs / maxOf(boundedPathoMs, 1e-6)).toString(),
        )

        assertTrue("Bounded distribution must complete under the 50ms budget (was $boundedMs ms)", boundedMs < 50.0)
        assertTrue("Bounded pathological shape must stay under budget (was $boundedPathoMs ms)", boundedPathoMs < 50.0)
    }

    /**
     * A union of [width] tables, each carrying one field whose type is itself a nested wide union
     * ([depth] levels). Disjoint field names per node force distinct (value,use) pairs at every
     * level, defeating the simple visited-pair collapse.
     */
    private fun nestedWideUnion(prefix: String, width: Int, depth: Int): LuaGraphType {
        if (depth == 0) return disjointUnion("${prefix}leaf", width)
        val inner = nestedWideUnion(prefix, width, depth - 1)
        val tables = (1..width).map { idx ->
            val memberNode = graph.variable(anchor)
            memberNode.upSet.add(graph.value(anchor, inner))
            memberNode.downSet.add(graph.use(anchor, inner))
            LuaGraphType.Table(null, mutableMapOf("$prefix${depth}_$idx" to memberNode), isExact = true)
        }
        return LuaGraphType.Union(tables.toSet())
    }

    /** Runs [block] on a worker; returns elapsed ms, or null if it exceeds [timeoutMs]. */
    private fun timedOrTimeout(timeoutMs: Long, block: () -> Unit): Double? {
        var elapsed: Double? = null
        val worker = Thread {
            val start = System.nanoTime()
            try {
                block()
                elapsed = (System.nanoTime() - start) / 1_000_000.0
            } catch (_: StackOverflowError) {
                elapsed = null
            }
        }
        worker.isDaemon = true
        worker.start()
        worker.join(timeoutMs)
        return if (worker.isAlive) null else elapsed
    }

    private fun disjointUnionFor(g: LuaTypeGraph, prefix: String, count: Int): LuaGraphType.Union {
        val tables = (1..count).map { idx ->
            val memberNode = g.variable(anchor)
            memberNode.downSet.add(g.use(anchor, LuaGraphType.Number))
            LuaGraphType.Table(null, mutableMapOf("$prefix$idx" to memberNode), isExact = true)
        }
        return LuaGraphType.Union(tables.toSet())
    }
}
