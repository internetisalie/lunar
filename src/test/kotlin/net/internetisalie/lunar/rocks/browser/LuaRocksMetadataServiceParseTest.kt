package net.internetisalie.lunar.rocks.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LuaRocksMetadataService] output parsing (TC-ROCKS-02-05 from requirements).
 *
 * No network, no running IDE — feeds raw strings to the internal parse helper.
 */
class LuaRocksMetadataServiceParseTest {

    @Test
    fun `TC-ROCKS-02-05 parses standard porcelain show output`() {
        val stdout = """
            package	inspect
            version	3.1.3-0
            summary	Human-readable representation of Lua tables
            license	MIT
            homepage	https://github.com/kikito/inspect.lua
            dependency	lua	>= 5.1
        """.trimIndent()

        val meta = LuaRocksMetadataService.parseShowOutput(stdout, "inspect")

        assertEquals("inspect", meta!!.name)
        assertEquals("3.1.3-0", meta.version)
        assertEquals("Human-readable representation of Lua tables", meta.summary)
        assertEquals("MIT", meta.license)
        assertEquals("https://github.com/kikito/inspect.lua", meta.homepage)
        assertEquals(listOf("lua >= 5.1"), meta.dependencies)
    }

    @Test
    fun `parses multiple dependencies`() {
        val stdout = """
            package	foo
            version	1.0-1
            dependency	lua	>= 5.1
            dependency	luasocket	>= 3.0
        """.trimIndent()

        val meta = LuaRocksMetadataService.parseShowOutput(stdout, "foo")!!
        assertEquals(listOf("lua >= 5.1", "luasocket >= 3.0"), meta.dependencies)
    }

    @Test
    fun `parses module entries`() {
        val stdout = """
            package	foo
            version	1.0-1
            module	foo.core	foo/core.lua
            module	foo.util	foo/util.lua
        """.trimIndent()

        val meta = LuaRocksMetadataService.parseShowOutput(stdout, "foo")!!
        assertEquals(listOf("foo.core", "foo.util"), meta.modules)
    }

    @Test
    fun `skips lines without a tab separator`() {
        val stdout = """
            package	foo
            version	1.0-1
            this line has no tab
            summary	A summary
        """.trimIndent()

        val meta = LuaRocksMetadataService.parseShowOutput(stdout, "foo")!!
        assertEquals("A summary", meta.summary)
    }

    @Test
    fun `returns null when version is absent`() {
        val stdout = "package\tfoo\nsummary\tSome summary\n"
        val meta = LuaRocksMetadataService.parseShowOutput(stdout, "foo")
        assertNull(meta)
    }

    @Test
    fun `uses fallback name when package key is absent`() {
        val stdout = "version\t1.0-1\nsummary\tSome summary\n"
        val meta = LuaRocksMetadataService.parseShowOutput(stdout, "fallback")!!
        assertEquals("fallback", meta.name)
    }

    @Test
    fun `handles empty detailed lines`() {
        val stdout = """
            package	foo
            version	1.0-1
            detailed	First paragraph.
            detailed	Second paragraph.
        """.trimIndent()

        val meta = LuaRocksMetadataService.parseShowOutput(stdout, "foo")!!
        assertEquals("First paragraph.\nSecond paragraph.", meta.detailed)
    }

    @Test
    fun `ignores unknown keys`() {
        val stdout = """
            package	foo
            version	1.0-1
            namespace	bar
            labels	some-label
            command	foo	foo.lua
        """.trimIndent()

        // Must not throw; known fields still parsed
        val meta = LuaRocksMetadataService.parseShowOutput(stdout, "foo")!!
        assertEquals("foo", meta.name)
        assertTrue(meta.modules.isEmpty())
    }

    @Test
    fun `dependency without label`() {
        val stdout = """
            package	foo
            version	1.0-1
            dependency	lua
        """.trimIndent()

        val meta = LuaRocksMetadataService.parseShowOutput(stdout, "foo")!!
        assertEquals(listOf("lua"), meta.dependencies)
    }
}
