package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
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
}
