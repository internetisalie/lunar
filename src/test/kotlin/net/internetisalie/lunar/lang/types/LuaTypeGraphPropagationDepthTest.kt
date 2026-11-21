package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.TypeNode
import net.internetisalie.lunar.lang.psi.types.VariableNode
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-103: `LuaTypeGraph`'s closure walk must not use one thread-stack frame per pair it adds.
 *
 * luals `script/parser/compile.lua` adds 8,099 pairs in one walk, which overflowed the 1 MB test
 * thread whenever the walk ran before the JIT had compiled it.
 */
@RunWith(JUnit4::class)
class LuaTypeGraphPropagationDepthTest : BasePlatformTestCase() {
    @Test
    fun aValueReachesTheEndOfALongDownstreamChain() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("chain.lua", "local v = 1\n")
        val chain = chain(graph, anchor)
        val value = graph.value(anchor, LuaGraphType.String)

        graph.addEdge(value, chain.first())

        assertTrue("every variable in the chain receives the value", chain.all { value in it.upSet })
    }

    @Test
    fun aUseReachesTheStartOfALongUpstreamChain() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("chain.lua", "local v = 1\n")
        val chain = chain(graph, anchor)
        val use = graph.use(anchor, LuaGraphType.String)

        graph.addEdge(chain.last(), use)

        assertTrue("every variable in the chain constrains the use", chain.all { use in it.downSet })
    }

    @Test
    fun closureOrderIsTheRecursiveWalksOrder() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("order.lua", "local v = 1\n")
        val names = mutableMapOf<TypeNode, String>()
        val variables = "abcde".map { name -> graph.variable(anchor).also { names[it] = "$name" } }
        val (a, b, c, d, e) = variables
        val x = graph.value(anchor, LuaGraphType.String).also { names[it] = "x" }
        val y = graph.value(anchor, LuaGraphType.Number).also { names[it] = "y" }
        val u = graph.use(anchor, LuaGraphType.Any).also { names[it] = "u" }
        val w = graph.use(anchor, LuaGraphType.Any).also { names[it] = "w" }
        listOf(x to a, a to b, c to d, b to u, y to c, b to c, d to w, e to a)
            .forEach { (from, to) -> graph.addEdge(from, to) }

        val orders = variables.map { "${it.upSet.render(names)}/${it.downSet.render(names)}" }

        assertEquals(EXPECTED_ORDERS, orders)
    }

    private fun Iterable<TypeNode>.render(names: Map<TypeNode, String>): String =
        joinToString("") { names.getValue(it) }

    private fun chain(
        graph: LuaTypeGraph,
        anchor: PsiElement,
    ): List<VariableNode> {
        val chain = List(CHAIN_LENGTH) { graph.variable(anchor) }
        chain.zipWithNext { upstream, downstream ->
            upstream.downSet.add(downstream)
            downstream.upSet.add(upstream)
        }
        return chain
    }

    private companion object {
        const val CHAIN_LENGTH = 200_000

        /**
         * `a` to `e`, each as `upSet/downSet`: the recursive walk's order, recorded on the base and
         * unchanged by BUG-97's cluster collapse. The fixture has no cycle: the members of a cycle share
         * one set, so there is no order of their own to compare.
         */
        val EXPECTED_ORDERS =
            listOf("xe/budcw", "axe/ucdw", "ybaxe/dw", "cyxabe/w", "/abucdw")
    }
}
