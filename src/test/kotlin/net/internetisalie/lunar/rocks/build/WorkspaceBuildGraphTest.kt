package net.internetisalie.lunar.rocks.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class WorkspaceBuildGraphTest {

    @Test
    fun testTestCase1Chain() {
        val rockA = WorkspaceRock("A", Path.of("a.rockspec"), listOf("lua"))
        val rockB = WorkspaceRock("B", Path.of("b.rockspec"), listOf("A"))
        val rockC = WorkspaceRock("C", Path.of("c.rockspec"), listOf("B"))

        val result = WorkspaceBuildGraph.topoSort(listOf(rockA, rockB, rockC))

        assertTrue(result is BuildPlan.Ordered)
        val ordered = (result as BuildPlan.Ordered).rocks
        assertEquals(3, ordered.size)
        assertEquals("A", ordered[0].packageName)
        assertEquals("B", ordered[1].packageName)
        assertEquals("C", ordered[2].packageName)
    }

    @Test
    fun testTestCase2Cycle() {
        val rockA = WorkspaceRock("A", Path.of("a.rockspec"), listOf("B"))
        val rockB = WorkspaceRock("B", Path.of("b.rockspec"), listOf("A"))

        val result = WorkspaceBuildGraph.topoSort(listOf(rockA, rockB))

        assertTrue(result is BuildPlan.Cycle)
        val cycle = (result as BuildPlan.Cycle).packages
        assertEquals(setOf("A", "B"), cycle)
    }

    @Test
    fun testTestCase3IndependentPair() {
        val rockX = WorkspaceRock("X", Path.of("x.rockspec"), listOf("lua"))
        val rockY = WorkspaceRock("Y", Path.of("y.rockspec"), listOf("lua"))

        val result = WorkspaceBuildGraph.topoSort(listOf(rockY, rockX))

        assertTrue(result is BuildPlan.Ordered)
        val ordered = (result as BuildPlan.Ordered).rocks
        assertEquals(2, ordered.size)
        assertEquals("X", ordered[0].packageName)
        assertEquals("Y", ordered[1].packageName)
    }

    @Test
    fun testTestCase4ExternalDependency() {
        val rockA = WorkspaceRock("A", Path.of("a.rockspec"), listOf("dkjson"))
        val rockB = WorkspaceRock("B", Path.of("b.rockspec"), emptyList())

        val result = WorkspaceBuildGraph.topoSort(listOf(rockA, rockB))

        assertTrue(result is BuildPlan.Ordered)
        val ordered = (result as BuildPlan.Ordered).rocks
        assertEquals(2, ordered.size)
        assertEquals("A", ordered[0].packageName)
        assertEquals("B", ordered[1].packageName)
    }

    @Test
    fun testEmptyInput() {
        val result = WorkspaceBuildGraph.topoSort(emptyList())
        assertEquals(BuildPlan.Empty, result)
    }
}
