package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-09-P0-03 — recursive-union termination (THROWAWAY de-risking spike).
 *
 * Question: does flattening/conversion/compatibility terminate on a self-referential union
 * conceptually `type T = T | number`?
 *
 * Two probes:
 *  1. A PSI fixture with a self-referencing `---@class`/alias, resolved through the real
 *     conversion + compatibility path (mirrors UnionAndGenericTest.testSelfReferencingClass).
 *  2. A directly-built graph where a class table's member union references the class itself,
 *     driving `resolveWrite`'s `flatten`, `getMembers`, and `addEdge` compatibility.
 *
 * Pass: no StackOverflowError; the recursive union resolves sensibly (members include number).
 */
@RunWith(JUnit4::class)
class LuaRecursiveUnionSpikeTest : IndexedBasePlatformTestCase() {

    @Test
    fun testSelfReferentialUnionAliasTerminates() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@class T
            ---@field self T | number
            local T = {}

            ---@type T | number
            local x = T
            local y = x
            """.trimIndent(),
        )

        // The whole conversion + checkTypes pipeline must terminate without SOE.
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Snapshot must be produced (no StackOverflow during build)", snapshot)
        // Reading errors forces the full graph evaluation.
        val errors = snapshot.getErrors()
        assertNotNull("Errors list must be retrievable", errors)
    }

    @Test
    fun testDirectlyBuiltRecursiveUnionFlattens() {
        val anchor: PsiElement = myFixture.addFileToProject("rec.lua", "")
        val graph = LuaTypeGraph()

        // Build a class table T whose member 'self' is the union T | number — self-referential.
        val memberNode = graph.variable(anchor)
        val tableT = LuaGraphType.Table(className = "T", localMembers = mapOf("self" to memberNode), isExact = true)
        val recursiveUnion = LuaGraphType.Union(setOf(tableT, LuaGraphType.Number))

        memberNode.upSet.add(graph.value(anchor, recursiveUnion))
        memberNode.downSet.add(graph.use(anchor, recursiveUnion))

        // 1. getMembers on the recursive union must terminate and expose 'self'.
        val members = recursiveUnion.getMembers()
        assertTrue("Union members should include the class field 'self'", members.containsKey("self"))

        // 2. resolveWrite's flatten must terminate on the cyclic member.
        val resolvedWrite = memberNode.write
        assertNotNull("resolveWrite must terminate and yield a type", resolvedWrite)

        // 3. Compatibility of the recursive union against itself must terminate (visited guard).
        val v = graph.value(anchor, recursiveUnion)
        val u = graph.use(anchor, recursiveUnion)
        graph.addEdge(v, u)
        // No SOE means we reached here; the union still carries number as a member.
        assertTrue(
            "Recursive union must retain the number alternative",
            recursiveUnion.types.contains(LuaGraphType.Number),
        )
        println("SPIKE-03 recursive-union members=${members.keys} resolvedWrite=${resolvedWrite.displayName()}")
    }
}
