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
        assertEquals("builtin", data.buildType)
        assertTrue(data.luaModules.isEmpty())
        assertTrue(data.cModules.isEmpty())
    }

    @Test
    fun parseBuildModulesTC1() {
        // TC #1: Bridge JSON {"package":"foo","build":{"type":"builtin","modules":{"foo.bar":"src/foo/bar.lua"}}}
        val stdout = """{"package":"foo","build":{"type":"builtin","modules":{"foo.bar":"src/foo/bar.lua"}}}"""
        val data = assertNotNull(RockspecBridge.parse(stdout, path))
        assertEquals("builtin", data.buildType)
        assertEquals(mapOf("foo.bar" to "src/foo/bar.lua"), data.luaModules)
        assertTrue(data.cModules.isEmpty())
    }

    @Test
    fun parseBuildModulesTC2() {
        // TC #2: Bridge JSON with no build field
        val stdout = """{"package":"foo"}"""
        val data = assertNotNull(RockspecBridge.parse(stdout, path))
        assertNull(data.buildType)
        assertTrue(data.luaModules.isEmpty())
        assertTrue(data.cModules.isEmpty())
    }

    @Test
    fun parseBuildModulesTC3() {
        // TC #3: Bridge JSON build.modules = {"cjson": ["src/cjson.c"]}
        val stdout = """{"package":"foo","build":{"type":"builtin","modules":{"cjson":["src/cjson.c"]}}}"""
        val data = assertNotNull(RockspecBridge.parse(stdout, path))
        assertEquals("builtin", data.buildType)
        assertTrue(data.luaModules.isEmpty())
        assertEquals(mapOf("cjson" to listOf("src/cjson.c")), data.cModules)
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
