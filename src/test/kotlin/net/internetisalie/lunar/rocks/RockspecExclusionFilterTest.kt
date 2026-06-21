package net.internetisalie.lunar.rocks

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure string-level truth table for [RockspecExclusionFilter] — no platform fixture.
 *
 * Covers ROCKS-09 TC #2 (lua_modules), #3 (build/output/thirdparty/.luarocks), #4 (filename
 * named `build-…` is NOT excluded), #10 (exclude glob), #11 (include allow-list).
 */
class RockspecExclusionFilterTest {

    private fun included(path: String, include: List<String> = emptyList(), exclude: List<String> = emptyList()) =
        RockspecExclusionFilter.isIncluded(path, include, exclude)

    // ---------------------------------------------------------------- TC #2

    @Test
    fun `root rockspec is included`() {
        assertTrue(included("foo-1.0-1.rockspec"))
    }

    @Test
    fun `lua_modules directory segment is excluded`() {
        assertFalse(included("lua_modules/share/lua/5.4/bar/bar-2.0-1.rockspec"))
    }

    // ---------------------------------------------------------------- TC #3

    @Test
    fun `build directory segment is excluded`() {
        assertFalse(included("build/foo-1.0-1.rockspec"))
    }

    @Test
    fun `build with suffix directory segment is excluded`() {
        assertFalse(included("build-5.4/foo-1.0-1.rockspec"))
    }

    @Test
    fun `output directory segment is excluded`() {
        assertFalse(included("output/foo-1.0-1.rockspec"))
    }

    @Test
    fun `thirdparty directory segment is excluded`() {
        assertFalse(included("thirdparty/vendored/foo-1.0-1.rockspec"))
    }

    @Test
    fun `dot luarocks directory segment is excluded`() {
        assertFalse(included(".luarocks/foo-1.0-1.rockspec"))
    }

    // ---------------------------------------------------------------- TC #4

    @Test
    fun `filename beginning with build is NOT excluded`() {
        assertTrue(included("src/build-tools-1.0-1.rockspec"))
    }

    @Test
    fun `nested kernel-shaped rockspec is included`() {
        assertTrue(included("rocks/adt/adt-1.0-1.rockspec"))
    }

    // ---------------------------------------------------------------- TC #10

    @Test
    fun `exclude glob drops matching path`() {
        assertFalse(included("vendor/v-1.0-1.rockspec", exclude = listOf("vendor/**")))
    }

    @Test
    fun `exclude glob keeps non-matching path`() {
        assertTrue(included("a/a-1.0-1.rockspec", exclude = listOf("vendor/**")))
    }

    // ---------------------------------------------------------------- TC #11

    @Test
    fun `include glob acts as allow-list`() {
        assertTrue(included("a/a-1.0-1.rockspec", include = listOf("a/**")))
        assertFalse(included("b/b-1.0-1.rockspec", include = listOf("a/**")))
    }

    @Test
    fun `built-in exclusion still applies under override globs`() {
        assertFalse(included("lua_modules/x/x-1.0-1.rockspec", include = listOf("**")))
    }

    @Test
    fun `invalid glob is treated as non-matching without throwing`() {
        assertTrue(included("a/a-1.0-1.rockspec", exclude = listOf("[")))
    }
}
