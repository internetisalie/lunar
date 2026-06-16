package net.internetisalie.lunar.rocks

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the real bridge JSON shape captured from `rockspec.lua` against a sample rockspec — a
 * single object with `package`/`version`/`dependencies` at top level, keys in arbitrary order.
 */
class RockspecBridgeParseTest {
    private val path = Path.of("busted-2.2.0-1.rockspec")

    @Test
    fun parsesRealBridgeOutput() {
        // Verbatim stdout from: lua rockspec.lua busted.rockspec (keys intentionally unordered).
        val stdout = """
            {"description":{"summary":"Elegant Lua unit testing.","license":"MIT"},
             "source":{"url":"git://github.com/lunarmodules/busted"},
             "package":"busted","build":{"type":"builtin"},
             "dependencies":["lua >= 5.1","say >= 1.4-3","luassert >= 1.9.0","lua_cliargs = 3.0","penlight >= 1.13.1"],
             "version":"2.2.0-1"}
        """.trimIndent()

        val data = assertNotNull(RockspecBridge.parse(stdout, path))
        assertEquals("busted", data.packageName)
        assertEquals("2.2.0-1", data.version)
        assertEquals(
            listOf("lua >= 5.1", "say >= 1.4-3", "luassert >= 1.9.0", "lua_cliargs = 3.0", "penlight >= 1.13.1"),
            data.dependencies,
        )
    }

    @Test
    fun missingDependenciesYieldsEmptyList() {
        val data = assertNotNull(RockspecBridge.parse("""{"package":"x","version":"1.0"}""", path))
        assertTrue(data.dependencies.isEmpty())
    }

    @Test
    fun platformMappedObjectDependenciesAreFlattened() {
        val stdout = """{"package":"x","version":"1.0","dependencies":{"unix":["a >= 1"],"win32":"b >= 2"}}"""
        val data = assertNotNull(RockspecBridge.parse(stdout, path))
        assertEquals(setOf("a >= 1", "b >= 2"), data.dependencies.toSet())
    }

    @Test
    fun invalidJsonReturnsNull() {
        assertNull(RockspecBridge.parse("not json", path))
    }

    @Test
    fun missingPackageReturnsNull() {
        assertNull(RockspecBridge.parse("""{"version":"1.0"}""", path))
    }
}
