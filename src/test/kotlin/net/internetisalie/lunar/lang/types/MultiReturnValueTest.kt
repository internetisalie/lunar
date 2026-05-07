package net.internetisalie.lunar.lang.types

import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for multi-return value handling and assignment.
 *
 * This test suite validates function return value handling when multiple values are returned:
 * - Multi-return type declarations
 * - Multi-value assignments from function calls
 * - Type checking in multi-value assignments
 * - Mismatch detection across return positions
 *
 * **Component**: Type Inference Engine - Phase 4 (Multi-Return Values)
 * **Feature**: Lua supports multiple return values; this ensures they are properly typed.
 */
@RunWith(JUnit4::class)
class MultiReturnValueTest : IndexedBasePlatformTestCase() {

    // =========================================================================
    // Multi-return declarations
    // =========================================================================

    @Test
    fun testMultiReturnBasic() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@return number
            ---@return string
            local function f()
                return 1, "hi"
            end

            local a, b = f()
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val errors = snapshot.getErrors()
        assertTrue("Should have no errors, but got: ${errors.map { it.message }}", errors.filter { it.severity != ErrorSeverity.WEAK_WARNING }.isEmpty())
    }

    @Test
    fun testMultiReturnMismatch() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@return number
            ---@return string
            local function f()
                return 1, 2 -- Error: 2nd return should be string
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val errors = snapshot.getErrors()
        val messages = errors.map { it.message }
        assertTrue("Expected type error but got: $messages",
            messages.any { it.contains("number") && it.contains("string") })
    }

    // =========================================================================
    // Multi-value assignments
    // =========================================================================

    @Test
    fun testMultiValueAssignment() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@return number
            ---@return string
            local function f() return 1, "a" end

            local n, s = 0, ""
            ---@type number
            n = 0
            ---@type string
            s = ""

            n, s = f() -- OK
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val errors = snapshot.getErrors().filter { it.severity != ErrorSeverity.WEAK_WARNING }
        assertTrue("Should have no errors in multi-assignment, but got: ${errors.map { it.message }}", errors.isEmpty())
    }

    @Test
    fun testMultiValueAssignmentError() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@return number
            ---@return string
            local function f() return 1, "a" end

            local n, s
            ---@type string
            n = ""
            n, s = f() -- Error: n is string, but f() returns number first
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val errors = snapshot.getErrors()
        assertFalse("Should have type mismatch error", errors.isEmpty())
        assertTrue("Error should mention number is not assignable to string, but got: ${errors.map { it.message }}",
            errors.any { it.message.contains("number") && it.message.contains("string") })
    }
}
