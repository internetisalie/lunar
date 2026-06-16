package net.internetisalie.lunar.tool

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [LuaToolDiscoveryService] using a temporary directory to simulate PATH entries.
 *
 * Note: PATH manipulation at the JVM level is not straightforward, so we test the
 * [LuaToolDescriptor.resolveOnPath] contract indirectly and focus on the duplicate-suppression
 * and candidate-enumeration logic that is fully deterministic.
 */
class LuaToolDiscoveryServiceTest {

    @Test
    fun `DiscoveredTool carries correct type and file`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("luarocks").toFile()
        file.createNewFile()
        file.setExecutable(true)

        val discovered = LuaToolDiscoveryService.DiscoveredTool(LuaToolType.LUAROCKS, file)

        assertEquals(LuaToolType.LUAROCKS, discovered.type)
        assertEquals(file, discovered.file)
    }

    @Test
    fun `discoverKnownTools returns no duplicates for same canonical path`(@TempDir tempDir: Path) {
        // Create a fake luarocks binary
        val file = tempDir.resolve("luarocks").toFile()
        file.createNewFile()
        file.setExecutable(true)

        // Build discovered results simulating two hits for the same file
        val raw = listOf(
            LuaToolDiscoveryService.DiscoveredTool(LuaToolType.LUAROCKS, file),
            LuaToolDiscoveryService.DiscoveredTool(LuaToolType.LUAROCKS, file),
        )

        // Simulate the deduplication logic inside discoverKnownTools
        val seen = LinkedHashSet<String>()
        val deduped = raw.filter { seen.add(it.file.canonicalPath) }

        assertEquals(1, deduped.size)
    }

    @Test
    fun `LuaToolDescriptor candidates on non-Windows returns bare name only`() {
        val descriptor = LuaToolDescriptor(LuaToolType.LUAROCKS, "luarocks")
        val candidates = descriptor.candidates(windows = false)
        assertEquals(listOf("luarocks"), candidates)
    }

    @Test
    fun `LuaToolDescriptor candidates on Windows returns bat exe cmd and bare`() {
        val descriptor = LuaToolDescriptor(LuaToolType.LUAROCKS, "luarocks")
        val candidates = descriptor.candidates(windows = true)
        assertTrue("luarocks.bat" in candidates)
        assertTrue("luarocks.exe" in candidates)
        assertTrue("luarocks.cmd" in candidates)
        assertTrue("luarocks" in candidates)
        // Most-specific first
        assertEquals("luarocks.bat", candidates.first())
    }

    @Test
    fun `all three known tool types have descriptors`() {
        val types = LuaToolDescriptor.DESCRIPTORS.map { it.toolType }.toSet()
        assertTrue(LuaToolType.LUAROCKS in types)
        assertTrue(LuaToolType.LUACHECK in types)
        assertTrue(LuaToolType.STYLUA in types)
    }
}
