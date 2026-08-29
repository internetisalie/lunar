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
     * BUG-473. The walk-root memo must be invisible to a GUARDED entry.
     *
     * `writeWith` receives the caller's cycle-guard set, so its result is walk-relative: a re-entry
     * owes `Undefined`, the neutral element of `resolveWrite`'s union fold. Serving the memo here
     * would be BUG-390 in reverse — ignoring the guard rather than losing it — and would resolve a
     * cycle to a type.
     */
    @Test
    fun guardedWriteEntryIgnoresTheMemo() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("guardedwrite.lua", "local s = \"a\"\n")
        val variable = graph.variable(anchor)
        graph.addEdge(graph.value(anchor, LuaGraphType.String), variable)

        assertEquals("the root accessor must resolve normally", LuaGraphType.String, variable.write)

        assertEquals(
            "a re-entry under the caller's guard owes Undefined, never the memoized type",
            LuaGraphType.Undefined,
            variable.writeWith(mutableSetOf(variable)),
        )
    }

    /**
     * BUG-473, the same constraint on the read walk. `resolveRead` is private, so the guarded entry
     * is reached the only way production reaches it: through a second variable's root read.
     *
     * `first` and `second` demand each other plus one primitive apiece. Rooted at `first` the answer
     * is String (`second`'s demand wins first place, `first`'s own re-entry folding to `Any`);
     * rooted at `second` it is Number, for the mirror reason. If the interior hop served `first`'s
     * memo the second assertion would read String.
     */
    @Test
    fun guardedReadEntryIgnoresTheMemo() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("guardedread.lua", "local a, b\n")
        val first = graph.variable(anchor)
        val second = graph.variable(anchor)
        first.downSet.add(second)
        first.downSet.add(graph.use(anchor, LuaGraphType.Number))
        second.downSet.add(first)
        second.downSet.add(graph.use(anchor, LuaGraphType.String))

        assertEquals("rooted at first, second's demand leads", LuaGraphType.String, first.read)

        assertEquals(
            "rooted at second, the guarded hop into first must fold to Any, not serve its memo",
            LuaGraphType.Number,
            second.read,
        )
    }

    /**
     * BUG-473. An edge added after a memoized read must be observed. The second value node is
     * created BEFORE the first read deliberately: node creation bumps the revision too, so creating
     * it later would invalidate the memo on its own and the test would pass without an edge bump.
     */
    @Test
    fun memoIsInvalidatedByALaterEdge() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("lateredge.lua", "local v\n")
        val variable = graph.variable(anchor)
        graph.addEdge(graph.value(anchor, LuaGraphType.String), variable)
        val laterValue = graph.value(anchor, LuaGraphType.Number)

        assertEquals(LuaGraphType.String, variable.write)

        graph.addEdge(laterValue, variable)

        assertEquals(
            "an edge added after the memo was filled must widen the resolved type",
            LuaGraphType.Union(setOf(LuaGraphType.String, LuaGraphType.Number)),
            variable.write,
        )
    }

    /**
     * BUG-473, the shape `LuaGraphType.memberNodeFor` builds: `upSet`/`downSet` mutated directly,
     * bypassing `addEdge` and its propagation entirely. The revision bump lives in [OrderedSet] so
     * this path invalidates without needing to be enumerated — a key wired only into
     * `propagateDownward`/`propagateUpward` would miss it.
     */
    @Test
    fun memoIsInvalidatedByAnEdgeAddedOutsideAddEdge() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("membernode.lua", "local v\n")
        val variable = graph.variable(anchor)
        variable.upSet.add(graph.value(anchor, LuaGraphType.String))
        val laterValue = graph.value(anchor, LuaGraphType.Number)

        assertEquals(LuaGraphType.String, variable.write)

        variable.upSet.add(laterValue)

        assertEquals(
            "a direct up-set mutation must invalidate the memo the same as addEdge does",
            LuaGraphType.Union(setOf(LuaGraphType.String, LuaGraphType.Number)),
            variable.write,
        )
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
