package net.internetisalie.lunar.rocks.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LuaRocksSearchService] output parsing (TC-ROCKS-02-04 from requirements).
 *
 * No network, no running IDE — feeds raw strings to the internal parse helpers.
 */
class LuaRocksSearchServiceParseTest {

    // ── parseSearchOutput ────────────────────────────────────────────────────

    @Test
    fun `TC-ROCKS-02-04 collapses arch variants and preserves distinct versions`() {
        val stdout = """
            inspect 3.1.3-0 rockspec https://luarocks.org/manifests/kikito
            inspect 3.1.3-0 src https://luarocks.org/manifests/kikito
            inspect 3.1.2-0 rockspec https://luarocks.org/manifests/kikito
        """.trimIndent()

        val results = LuaRocksSearchService.parseSearchOutput(stdout, emptySet())

        // Two distinct (name, version) entries
        assertEquals(2, results.size)
        assertEquals("inspect", results[0].name)
        assertEquals("3.1.3-0", results[0].version)
        assertEquals("inspect", results[1].name)
        assertEquals("3.1.2-0", results[1].version)
    }

    @Test
    fun `TC-ROCKS-16-11 redesign did not alter arch collapse`() {
        // Regression guard: two arch rows for the same (name, version) collapse to one row.
        val stdout = """
            inspect 3.1.3-0 rockspec https://luarocks.org/manifests/kikito
            inspect 3.1.3-0 src https://luarocks.org/manifests/kikito
        """.trimIndent()

        val results = LuaRocksSearchService.parseSearchOutput(stdout, emptySet())

        assertEquals(1, results.size)
        assertEquals("inspect", results[0].name)
        assertEquals("3.1.3-0", results[0].version)
    }

    @Test
    fun `skips lines with fewer than 4 fields`() {
        val stdout = """
            inspect 3.1.3-0 rockspec https://luarocks.org/manifests/kikito
            bad-line-only-three-tokens here x
            also_bad

            good 1.0-1 src https://luarocks.org
        """.trimIndent()

        // "bad-line-only-three-tokens here x" has 3 fields  → skipped
        // "also_bad" has 1 field → skipped
        // blank line → skipped
        val results = LuaRocksSearchService.parseSearchOutput(stdout, emptySet())
        assertEquals(2, results.size)
        assertEquals("inspect", results[0].name)
        assertEquals("good", results[1].name)
    }

    @Test
    fun `marks installed packages`() {
        val stdout = "inspect 3.1.3-0 rockspec https://luarocks.org/manifests/kikito\n"
        val results = LuaRocksSearchService.parseSearchOutput(stdout, setOf("inspect"))
        assertEquals(1, results.size)
        assertTrue(results[0].isInstalled)
    }

    @Test
    fun `not installed when name absent from installed set`() {
        val stdout = "inspect 3.1.3-0 rockspec https://luarocks.org/manifests/kikito\n"
        val results = LuaRocksSearchService.parseSearchOutput(stdout, emptySet())
        assertTrue(!results[0].isInstalled)
    }

    @Test
    fun `parses optional namespace field`() {
        val stdout = "inspect 3.1.3-0 rockspec https://luarocks.org kikito\n"
        val results = LuaRocksSearchService.parseSearchOutput(stdout, emptySet())
        assertEquals("kikito", results[0].namespace)
    }

    @Test
    fun `missing namespace defaults to empty string`() {
        val stdout = "inspect 3.1.3-0 rockspec https://luarocks.org\n"
        val results = LuaRocksSearchService.parseSearchOutput(stdout, emptySet())
        assertEquals("", results[0].namespace)
    }

    @Test
    fun `empty stdout returns empty list`() {
        val results = LuaRocksSearchService.parseSearchOutput("", emptySet())
        assertTrue(results.isEmpty())
    }

    // ── parseInstalledOutput ─────────────────────────────────────────────────

    @Test
    fun `parseInstalledOutput extracts package names`() {
        val stdout = """
            inspect 3.1.3-0 installed /usr/local/lib/luarocks/rocks-5.4
            luasocket 3.0rc1-2 installed /usr/local/lib/luarocks/rocks-5.4
        """.trimIndent()

        val names = LuaRocksSearchService.parseInstalledOutput(stdout)
        assertEquals(setOf("inspect", "luasocket"), names)
    }

    @Test
    fun `parseInstalledOutput ignores blank lines`() {
        val stdout = "inspect 3.1.3-0 installed /path\n\n\nluasocket 3.0rc1-2 installed /path\n"
        val names = LuaRocksSearchService.parseInstalledOutput(stdout)
        assertEquals(2, names.size)
    }

    @Test
    fun `parseInstalledOutput empty stdout returns empty set`() {
        val names = LuaRocksSearchService.parseInstalledOutput("")
        assertTrue(names.isEmpty())
    }
}
