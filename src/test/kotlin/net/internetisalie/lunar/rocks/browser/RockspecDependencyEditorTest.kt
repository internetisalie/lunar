package net.internetisalie.lunar.rocks.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ROCKS-16-13 (DR-05): [RockspecDependencyEditor.addDependency] inserts a dependency into a
 * rockspec's `dependencies` table across the single-line and multi-line forms, is idempotent, and
 * appends a fresh block when none exists.
 */
class RockspecDependencyEditorTest {

    @Test
    fun `appends to a single-line dependencies block`() {
        val out = RockspecDependencyEditor.addDependency("dependencies = { \"lua >= 5.1\" }\n", "inspect >= 3.1")
        assertTrue(out, out.contains("\"lua >= 5.1\""))
        assertTrue(out, out.contains("\"inspect >= 3.1\""))
    }

    @Test
    fun `appends to a multi-line dependencies block`() {
        val src = """
            dependencies = {
               "lua >= 5.1",
            }
        """.trimIndent()
        val out = RockspecDependencyEditor.addDependency(src, "inspect >= 3.1")
        assertTrue(out, out.contains("\"lua >= 5.1\""))
        assertTrue(out, out.contains("\"inspect >= 3.1\""))
    }

    @Test
    fun `is idempotent when the package is already listed`() {
        val src = "dependencies = { \"inspect >= 3.1\" }\n"
        assertEquals(src, RockspecDependencyEditor.addDependency(src, "inspect >= 4.0"))
    }

    @Test
    fun `matches the package name regardless of constraint`() {
        val src = "dependencies = { \"inspect\" }\n"
        assertEquals(src, RockspecDependencyEditor.addDependency(src, "inspect >= 3.1"))
    }

    @Test
    fun `appends a fresh block when none exists`() {
        val out = RockspecDependencyEditor.addDependency("package = \"x\"\nversion = \"1.0-1\"\n", "inspect >= 3.1")
        assertTrue(out, out.contains("dependencies = { \"inspect >= 3.1\" }"))
    }

    @Test
    fun `fills an empty dependencies block`() {
        val out = RockspecDependencyEditor.addDependency("dependencies = {}\n", "inspect >= 3.1")
        assertTrue(out, out.contains("\"inspect >= 3.1\""))
    }
}
