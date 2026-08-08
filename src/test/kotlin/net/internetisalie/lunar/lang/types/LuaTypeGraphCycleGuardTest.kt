package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-390 — the write-resolution cycle guard must survive a hop through a lazy node.
 *
 * `VariableElement.resolveWrite` carries a `visited` set, but a `LazyValueElement` used to resolve
 * its receiver through the plain `write` accessor, which starts a *fresh* set. A cycle
 * `variable → lazy subscript → same variable` therefore recursed until the stack was exhausted,
 * killing the highlight pass on 30–43% of files across the MAINT-33 corpus.
 */
@RunWith(JUnit4::class)
class LuaTypeGraphCycleGuardTest : BasePlatformTestCase() {
    @Test
    fun lazyNodeCycleTerminatesInsteadOfOverflowing() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("cycle.lua", "local t = {}\nt[1] = t\n")
        val variable = graph.variable(anchor)

        // The cycle: the variable's only inbound edge is a lazy node whose computation resolves
        // the variable itself — exactly the shape seedSubscriptElement builds for `t[1] = t`.
        val lazy = graph.lazyValue(anchor) { visited -> variable.writeWith(visited) }
        variable.upSet.add(lazy)

        // Before the fix this threw StackOverflowError rather than returning.
        assertEquals(
            "A cycle routed through a lazy node must resolve to Undefined, not recurse",
            LuaGraphType.Undefined,
            variable.write,
        )
    }

    @Test
    fun lazyNodeStillResolvesAnAcyclicReceiver() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("acyclic.lua", "local t = {}\n")
        val source = graph.variable(anchor)
        source.upSet.add(graph.value(anchor, LuaGraphType.String))

        // Guarding the cycle must not break the ordinary case the lazy node exists for.
        val lazy = graph.lazyValue(anchor) { visited -> source.writeWith(visited) }
        val target = graph.variable(anchor)
        target.upSet.add(lazy)

        assertEquals(LuaGraphType.String, target.write)
    }

    /**
     * BUG-427. `graphTypeToLuaType` registered its cycle placeholder for a Function only on the way
     * *out*, unlike tables — so a function type reachable from its own parameter or return recursed
     * until the stack died. Latent until BUG-427 made `setfenv`/`rawlen` resolvable, at which point
     * luacheck's one-line `(setfenv and rawlen)(setfenv and rawlen)` sample killed the highlight
     * pass outright.
     */
    @Test
    fun selfReferentialFunctionTypeConvertsInsteadOfOverflowing() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("fncycle.lua", "local f\n")
        val node = graph.variable(anchor)

        // The function's own parameter and return ARE the node whose value is the function.
        val selfReferential =
            LuaGraphType.Function(
                listOf(LuaGraphType.Function.Parameter(node)),
                listOf(node),
            )
        graph.addEdge(graph.value(anchor, selfReferential), node)

        val converted = LuaTypesSnapshot(graph, emptyMap(), LuaGraphType.Any).graphTypeToLuaType(selfReferential)
        assertNotNull("a self-referential function type must convert, not overflow", converted)
    }
}
