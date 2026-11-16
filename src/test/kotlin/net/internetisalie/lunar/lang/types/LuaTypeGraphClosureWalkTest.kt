package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.UseNode
import net.internetisalie.lunar.lang.psi.types.ValueNode
import net.internetisalie.lunar.lang.psi.types.VariableNode
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-97 — the `declaredDemand` and `write` walks use the transitive closure of the up- and down-sets
 * instead of re-walking every set of a cluster.
 *
 * luals `script/parser/compile.lua` joins the `start` and `finish` fields of its syntax-tree tables
 * into one cluster of about 860 variables. Every variable of such a cluster holds every other in its
 * sets, so a walk that iterated the set of each variable it reached cost O(k²) per root. The number
 * of set entries a walk iterates is deterministic, so it is asserted rather than a time.
 */
@RunWith(JUnit4::class)
class LuaTypeGraphClosureWalkTest : BasePlatformTestCase() {
    @Test
    fun declaredDemandReadsOnlyTheRootsDownSet() {
        val cluster = Cluster(myFixture.addFileToProject("demand.lua", "local v\n"), Shape.CHAIN)
        val before = cluster.graph.walkStepCount()

        cluster.variables.forEach { it.declaredDemand }

        val ownEntries = cluster.variables.sumOf { it.downSet.size }.toLong()
        val steps = cluster.graph.walkStepCount() - before
        assertTrue("at most each root's own down-set, $ownEntries entries; iterated $steps", steps <= ownEntries)
    }

    /**
     * Each root still walks depth first in the old order, so the variables met before the closure
     * is complete are iterated in full: the saving is the tail, not the whole quadratic.
     */
    @Test
    fun writeStopsOnceTheClosureIsMet() {
        val cluster = Cluster(myFixture.addFileToProject("write.lua", "local v\n"), Shape.CHAIN)
        val before = cluster.graph.walkStepCount()

        cluster.variables.forEach { it.write }

        val fullWalks = CLUSTER_SIZE.toLong() * cluster.variables.sumOf { it.upSet.size }
        val steps = cluster.graph.walkStepCount() - before
        assertTrue(
            "under half of $fullWalks entries, every up-set at every root; iterated $steps",
            2 * steps < fullWalks,
        )
    }

    @Test
    fun walksMatchTheRecursiveWalksAtEveryRoot() {
        Shape.entries.forEach { shape ->
            val cluster = Cluster(myFixture.addFileToProject("${shape.name}.lua", "local v\n"), shape)
            cluster.variables.forEachIndexed { index, variable ->
                val expected = nestedWrite(variable, mutableSetOf())
                assertEquals("$shape write at $index", expected, variable.write)
                assertEquals("$shape union order at $index", expected.displayName(), variable.write.displayName())
                assertEquals(
                    "$shape declaredDemand at $index",
                    nestedDeclared(variable, mutableSetOf()),
                    variable.declaredDemand,
                )
            }
        }
    }

    /** How the cluster's variables are wired both ways: in a line, or each to one hub, as a shared field is. */
    private enum class Shape { CHAIN, STAR }

    /**
     * [CLUSTER_SIZE] variables wired both ways, a value entering at three of them, an undeclared use
     * leaving the first and a declared use leaving the last.
     */
    private class Cluster(
        anchor: PsiElement,
        shape: Shape,
    ) {
        val graph = LuaTypeGraph()
        val variables: List<VariableNode> = List(CLUSTER_SIZE) { graph.variable(anchor) }

        init {
            pairs(shape).forEach { (left, right) ->
                graph.addEdge(left, right)
                graph.addEdge(right, left)
            }
            VALUE_TYPES.forEachIndexed { index, type ->
                graph.addEdge(graph.value(anchor, type), variables[index * CLUSTER_SIZE / VALUE_TYPES.size])
            }
            graph.addEdge(variables.first(), graph.use(anchor, LuaGraphType.String))
            graph.addEdge(variables.last(), graph.use(anchor, LuaGraphType.Number, declaredDemand = true))
        }

        private fun pairs(shape: Shape): List<Pair<VariableNode, VariableNode>> =
            when (shape) {
                Shape.CHAIN -> variables.zipWithNext()
                Shape.STAR -> variables.drop(1).map { variables.first() to it }
            }
    }

    private companion object {
        const val CLUSTER_SIZE = 40

        val VALUE_TYPES = listOf(LuaGraphType.String, LuaGraphType.Number, LuaGraphType.Table(className = "C"))

        /** The walk before BUG-97, which recursed into every variable of the down-set. */
        fun nestedDeclared(
            variable: VariableNode,
            visited: MutableSet<VariableNode>,
        ): Boolean {
            if (!visited.add(variable)) return false
            return variable.downSet.any {
                when (it) {
                    is VariableNode -> nestedDeclared(it, visited)
                    is UseNode -> it.declaredDemand
                    else -> false
                }
            }
        }

        /** The walk before BUG-96: a set per variable, returned as a union and flattened again by its caller. */
        fun nestedWrite(
            variable: VariableNode,
            visited: MutableSet<VariableNode>,
        ): LuaGraphType {
            if (!visited.add(variable)) return LuaGraphType.Undefined
            val types = LinkedHashSet<LuaGraphType>()
            variable.upSet.forEach { node ->
                val type =
                    when (node) {
                        is VariableNode -> nestedWrite(node, visited)
                        is ValueNode -> node.writeWith(visited)
                        else -> LuaGraphType.Undefined
                    }
                flattenInto(type, types)
            }
            return when {
                types.isEmpty() -> LuaGraphType.Undefined
                types.size == 1 -> types.first()
                else -> LuaGraphType.Union(types)
            }
        }

        fun flattenInto(
            type: LuaGraphType,
            types: MutableSet<LuaGraphType>,
        ) {
            when (type) {
                is LuaGraphType.Union -> type.types.forEach { flattenInto(it, types) }
                LuaGraphType.Undefined -> Unit
                else -> types.add(type)
            }
        }
    }
}
