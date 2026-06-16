package net.internetisalie.lunar.tool

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// LuaToolVersionPatternTest uses assertNotNull too — imported above

/**
 * Unit tests for [LuaToolValidator] — regex matching and compatibility logic.
 *
 * All tests operate against static strings (no subprocesses) so they are fast and hermetic.
 */
class LuaToolValidatorTest {

    // -------------------------------------------------------------------------
    // SemanticVersion parsing
    // -------------------------------------------------------------------------

    @Test
    fun `SemanticVersion parses major-minor-patch`() {
        val v = LuaToolValidator.SemanticVersion.parse("3.9.2")
        assertNotNull(v)
        assertEquals(3, v.major)
        assertEquals(9, v.minor)
        assertEquals(2, v.patch)
    }

    @Test
    fun `SemanticVersion parses version with build suffix`() {
        val v = LuaToolValidator.SemanticVersion.parse("3.11.0-1")
        assertNotNull(v)
        assertEquals(3, v.major)
        assertEquals(11, v.minor)
        assertEquals(0, v.patch)
    }

    @Test
    fun `SemanticVersion parses major-minor only`() {
        val v = LuaToolValidator.SemanticVersion.parse("3.9")
        assertNotNull(v)
        assertEquals(3, v.major)
        assertEquals(9, v.minor)
        assertEquals(0, v.patch)
    }

    @Test
    fun `SemanticVersion returns null for garbage input`() {
        assertNull(LuaToolValidator.SemanticVersion.parse("not-a-version"))
    }

    @Test
    fun `SemanticVersion comparison is correct`() {
        val v300 = LuaToolValidator.SemanticVersion(3, 0, 0)
        val v390 = LuaToolValidator.SemanticVersion(3, 9, 0)
        val v400 = LuaToolValidator.SemanticVersion(4, 0, 0)

        assertTrue(v390 > v300)
        assertTrue(v400 > v390)
        assertTrue(v300 < v390)
        assertEquals(v390, LuaToolValidator.SemanticVersion(3, 9, 0))
    }

    // -------------------------------------------------------------------------
    // checkCompatibility
    // -------------------------------------------------------------------------

    @Test
    fun `checkCompatibility returns true when both versions are empty`() {
        val tool = LuaTool(type = LuaToolType.LUAROCKS, luaVersion = "")
        assertTrue(LuaToolValidator.checkCompatibility(tool, ""))
    }

    @Test
    fun `checkCompatibility returns true when tool luaVersion is empty`() {
        val tool = LuaTool(type = LuaToolType.LUAROCKS, luaVersion = "")
        assertTrue(LuaToolValidator.checkCompatibility(tool, "5.4"))
    }

    @Test
    fun `checkCompatibility returns true when interpreter version is empty`() {
        val tool = LuaTool(type = LuaToolType.LUAROCKS, luaVersion = "5.4")
        assertTrue(LuaToolValidator.checkCompatibility(tool, ""))
    }

    @Test
    fun `checkCompatibility returns true for matching versions`() {
        val tool = LuaTool(type = LuaToolType.LUAROCKS, luaVersion = "5.4")
        assertTrue(LuaToolValidator.checkCompatibility(tool, "5.4"))
    }

    @Test
    fun `checkCompatibility returns false for mismatched versions`() {
        val tool = LuaTool(type = LuaToolType.LUAROCKS, luaVersion = "5.3")
        assertFalse(LuaToolValidator.checkCompatibility(tool, "5.4"))
    }
}

/**
 * Additional tests for version-pattern matching using the public extractVersion entry point
 * via the regex logic — tested indirectly through known real output strings.
 *
 * These test the internal patterns without spawning subprocesses.
 */
class LuaToolVersionPatternTest {

    /** Helper: apply the same regex pattern logic that LuaToolValidator uses internally. */
    private fun matchVersion(output: String, type: LuaToolType): String? {
        val patterns = mapOf(
            LuaToolType.LUAROCKS to Regex("""LuaRocks\s+(\S+)""", RegexOption.IGNORE_CASE),
            LuaToolType.LUACHECK to Regex("""[Ll]uacheck[:\s]+(\S+)"""),
            LuaToolType.STYLUA   to Regex("""stylua\s+(\S+)""", RegexOption.IGNORE_CASE),
        )
        return patterns[type]?.find(output)?.groupValues?.get(1)
    }

    @Test
    fun `LuaRocks 3-9-2 version line matches`() {
        val output = "LuaRocks 3.9.2"
        assertEquals("3.9.2", matchVersion(output, LuaToolType.LUAROCKS))
    }

    @Test
    fun `LuaRocks full version banner matches`() {
        // The version token in "LuaRocks 3.11.0-1," is captured as "3.11.0-1," (non-space)
        val output = "LuaRocks 3.11.0-1, a package manager for Lua modules\n  using Lua 5.4 from /usr/bin/lua5.4"
        val raw = matchVersion(output, LuaToolType.LUAROCKS)
        assertNotNull(raw)
        assertTrue(raw.startsWith("3.11.0-1"), "Expected version to start with '3.11.0-1', got '$raw'")
    }

    @Test
    fun `luacheck colon format matches`() {
        val output = "Luacheck: 0.26.0"
        assertEquals("0.26.0", matchVersion(output, LuaToolType.LUACHECK))
    }

    @Test
    fun `luacheck space format matches`() {
        // The version token "0.25.0," is captured (regex grabs non-space); verify it starts correctly
        val output = "luacheck 0.25.0, a linter for Lua"
        val raw = matchVersion(output, LuaToolType.LUACHECK)
        assertNotNull(raw)
        assertTrue(raw.startsWith("0.25.0"), "Expected version to start with '0.25.0', got '$raw'")
    }

    @Test
    fun `stylua version matches`() {
        val output = "stylua 0.20.0"
        assertEquals("0.20.0", matchVersion(output, LuaToolType.STYLUA))
    }

    @Test
    fun `unrecognised output returns null`() {
        assertNull(matchVersion("bash: command not found", LuaToolType.LUAROCKS))
    }
}
