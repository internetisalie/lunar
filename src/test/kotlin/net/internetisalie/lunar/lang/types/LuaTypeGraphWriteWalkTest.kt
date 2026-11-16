package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.ValueNode
import net.internetisalie.lunar.lang.psi.types.VariableNode
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-96 — a `write` walk offers each reaching value to its result once, however many variables of
 * a cluster hold it.
 *
 * fallo's `init.lua` wires its generic class copies into clusters whose up-sets reach 973 nodes.
 * The up-sets are transitively closed, so every variable of such a cluster holds every value, and the
 * walk used to hash each value once per variable and again at every level it was returned
 * through. Counting what the walk offers to its result is deterministic, so it is asserted rather
 * than a time.
 */
@RunWith(JUnit4::class)
class LuaTypeGraphWriteWalkTest : BasePlatformTestCase() {
    @Test
    fun clusterWalkOffersEachValueOnce() {
        val cluster = Cluster(myFixture.addFileToProject("cluster.lua", "local v\n"))
        val before = cluster.graph.writeInsertionCount()

        cluster.variables.first().write

        assertEquals(
            "one insertion per distinct value, not one per variable holding it",
            VALUE_TYPES.size.toLong(),
            cluster.graph.writeInsertionCount() - before,
        )
    }

    @Test
    fun clusterWalkMatchesTheNestedWalkAtEveryRoot() {
        val cluster = Cluster(myFixture.addFileToProject("order.lua", "local v\n"))

        cluster.variables.forEach { variable ->
            val expected = nestedWrite(variable, mutableSetOf())
            val actual = variable.write
            assertEquals(expected, actual)
            assertEquals("the union keeps first-encounter order", expected.displayName(), actual.displayName())
        }
    }

    /** [CLUSTER_SIZE] variables wired both ways in a chain, a value entering at three of them and a lazy node at a fourth. */
    private class Cluster(
        anchor: PsiElement,
    ) {
        val graph = LuaTypeGraph()
        val variables: List<VariableNode> = List(CLUSTER_SIZE) { graph.variable(anchor) }

        init {
            variables.zipWithNext().forEach { (left, right) ->
                graph.addEdge(left, right)
                graph.addEdge(right, left)
            }
            VALUE_TYPES.forEachIndexed { index, type ->
                graph.addEdge(graph.value(anchor, type), variables[index * CLUSTER_SIZE / VALUE_TYPES.size])
            }
            graph.addEdge(graph.lazyValue(anchor) { LuaGraphType.Undefined }, variables[CLUSTER_SIZE / 2])
        }
    }

    private companion object {
        const val CLUSTER_SIZE = 40

        val VALUE_TYPES = listOf(LuaGraphType.String, LuaGraphType.Number, LuaGraphType.Table(className = "C"))

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
