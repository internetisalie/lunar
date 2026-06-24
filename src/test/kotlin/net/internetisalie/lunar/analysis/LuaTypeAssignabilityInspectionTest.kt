package net.internetisalie.lunar.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Real-flow tests for [LuaTypeAssignabilityInspection].
 *
 * Unlike [LuaTypeAssignabilityTest] (which calls `LuaTypesSnapshot.forFile` directly), these drive
 * the platform inspection machinery — `enableInspections(...)` + `doHighlighting()` — so a broken
 * registration, severity, element-pinning, or reporting path is actually caught. The snapshot-only
 * tests cannot see any of that: they read the engine's error list, not what the IDE shows the user.
 */
@RunWith(JUnit4::class)
class LuaTypeAssignabilityInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
    }

    /** Descriptions of all problems the highlighting pass surfaces for [text]. */
    private fun descriptions(text: String): List<String> {
        myFixture.configureByText("test.lua", text)
        return myFixture.doHighlighting().mapNotNull { it.description }
    }

    /** A call with too few arguments must be reported by the inspection (not just the snapshot). */
    @Test
    fun testArityTooFewReported() {
        val descs = descriptions(
            """
            ---@param a number
            ---@param b number
            local function add(a, b) end

            add(1)
            """.trimIndent(),
        )
        assertTrue(
            "Expected a 'Too few arguments' problem from the inspection, got: $descs",
            descs.any { it.contains("Too few arguments") },
        )
    }

    /** A table literal missing a required `@field` must be reported. */
    @Test
    fun testMissingRequiredFieldReported() {
        val descs = descriptions(
            """
            ---@class User
            ---@field id number
            ---@field username string
            local User = {}

            ---@type User
            local u = { id = 1 }
            """.trimIndent(),
        )
        assertTrue(
            "Expected a missing-required-field problem for 'username', got: $descs",
            descs.any { it.contains("Missing required field") && it.contains("username") },
        )
    }

    /** An optional (`| nil`) field omitted from a table literal must NOT be reported. */
    @Test
    fun testOptionalFieldNotReported() {
        val descs = descriptions(
            """
            ---@class User
            ---@field id number
            ---@field email string | nil
            local User = {}

            ---@type User
            local u = { id = 1 }
            """.trimIndent(),
        )
        assertFalse(
            "Optional field 'email' must not be reported as missing, got: $descs",
            descs.any { it.contains("email") },
        )
    }

    /** A scalar type mismatch (`number` assigned to `---@type string`) must be reported (TC1). */
    @Test
    fun testScalarTypeMismatchReported() {
        val descs = descriptions(
            """
            ---@type string
            local x = 42
            """.trimIndent(),
        )
        assertTrue(
            "Expected a 'not assignable' problem mentioning 'number' and 'string', got: $descs",
            descs.any { it.contains("not assignable") && it.contains("number") && it.contains("string") },
        )
    }

    @Test
    fun testBooleanConcatMismatchReported() {
        myFixture.configureByText(
            "test.lua",
            """
            local x = true
            local y = x .. "a"
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        val assignabilityErrors = highlights.filter { it.description?.contains("not assignable") == true }
        assertEquals("Expected exactly one assignability error", 1, assignabilityErrors.size)
        val err = assignabilityErrors[0]
        assertEquals("x .. \"a\"", err.text)
        assertTrue(err.description.contains("boolean") && err.description.contains("string"))
    }

    /**
     * A value whose type is a member of the declared union (`number` into `string|number`) must
     * NOT be reported (TC2).
     */
    @Test
    fun testUnionMemberMatchNotReported() {
        val descs = descriptions(
            """
            ---@type string|number
            local x = 42
            """.trimIndent(),
        )
        assertFalse(
            "A union member match must not produce a 'not assignable' warning, got: $descs",
            descs.any { it.contains("not assignable") },
        )
    }

    /**
     * TYPE-09 union diagnostics on REAL Lua (the union-distribution unit tests build graphs by
     * hand). A table that fails a union should name the closest-matching member and its missing
     * field, e.g. "closest match 'Point': missing field 'y'".
     */
    @Test
    fun testUnionClosestMatchDiagnosticOnRealCode() {
        val descs = descriptions(
            """
            ---@class Point
            ---@field x number
            ---@field y number
            local Point = {}

            ---@class Color
            ---@field r number
            ---@field g number
            ---@field b number
            local Color = {}

            ---@type Point | Color
            local p = { x = 1 }
            """.trimIndent(),
        )
        assertTrue(
            "Expected a union closest-match diagnostic naming 'Point' and the missing 'y', got: $descs",
            descs.any { it.contains("Point") && it.contains("y") },
        )
    }
}
