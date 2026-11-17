package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.VariableNode
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-96 — a table check builds the value's member map once, not once per member of the use.
 *
 * `LuaGraphType.getMembers` copies every supertype's members into a new map, so building it per use
 * member made a check O(use members × value members). On fallo `init.lua`, with variables
 * joined both ways collapsed, that was 41 % of check samples. The value table's member map
 * counts how often it is copied; the count must not grow with the number of members the use
 * demands.
 */
@RunWith(JUnit4::class)
class LuaTableCheckMemberMapTest : BasePlatformTestCase() {
    /** A value meeting a table demand: `checkTableCompatibility`. */
    @Test
    fun tableCheckReadsTheValueMembersOnceWhateverTheUseDemands() {
        val anchor = myFixture.configureByText("check.lua", "local v\n")

        val reads = MEMBER_COUNTS.map { copiesOfValueMembers(anchor, it) { use -> use } }

        assertEquals("value member map copies for $MEMBER_COUNTS use members", reads.first(), reads.last())
    }

    /** A value meeting a union demand, whose table arm is tried first: `isStructurallyCompatible`. */
    @Test
    fun structuralCompatibilityReadsTheValueMembersOnceWhateverTheUseDemands() {
        val anchor = myFixture.configureByText("structural.lua", "local v\n")

        val reads =
            MEMBER_COUNTS.map { count ->
                copiesOfValueMembers(
                    anchor,
                    count,
                ) { use -> LuaGraphType.Union.create(listOf(use, LuaGraphType.Number)) }
            }

        assertEquals("value member map copies for $MEMBER_COUNTS use members", reads.first(), reads.last())
    }

    /** Copies of the value's members made while a [MEMBER_COUNT]-member value meets a [useCount]-member demand. */
    private fun copiesOfValueMembers(
        anchor: PsiElement,
        useCount: Int,
        demand: (LuaGraphType.Table) -> LuaGraphType,
    ): Int {
        val graph = LuaTypeGraph()
        val valueMembers = CountingMembers()
        names(MEMBER_COUNT).forEach { valueMembers[it] = member(graph, anchor) }
        val useMembers = names(useCount).associateWith { member(graph, anchor) }
        val value = LuaGraphType.Table(className = "Value", localMembers = valueMembers)
        valueMembers.copies = 0

        graph.addEdge(
            graph.value(anchor, value),
            graph.use(anchor, demand(LuaGraphType.Table(localMembers = useMembers))),
        )

        return valueMembers.copies
    }

    private fun member(
        graph: LuaTypeGraph,
        anchor: PsiElement,
    ): VariableNode {
        val node = graph.variable(anchor)
        graph.addEdge(graph.value(anchor, LuaGraphType.Number), node)
        return node
    }

    private fun names(count: Int): List<String> = List(count) { "field$it" }

    /** A member map that counts each time its entries are read, which is how `getMembers` copies it. */
    private class CountingMembers : LinkedHashMap<String, VariableNode>() {
        var copies = 0

        override val entries: MutableSet<MutableMap.MutableEntry<String, VariableNode>>
            get() {
                copies++
                return super.entries
            }
    }

    private companion object {
        const val MEMBER_COUNT = 8

        val MEMBER_COUNTS = listOf(1, MEMBER_COUNT)
    }
}
