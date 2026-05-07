package net.internetisalie.lunar.lang.types

import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for function call signature matching and validation.
 *
 * This test suite validates function signature compatibility checking:
 * - Arity validation (correct number of arguments)
 * - Optional and variadic parameters
 * - Argument type checking
 * - Function type contravariance rules
 *
 * **Component**: Type Inference Engine - Phase 3 (Function Signature Matching)
 * **Specification**: TYPE-03 - Function Call Type Checking
 */
@RunWith(JUnit4::class)
class FunctionSignatureMatchingTest : IndexedBasePlatformTestCase() {

    // =========================================================================
    // Arity validation
    // =========================================================================

    @Test
    fun testArityTooFew() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@param a number
            ---@param b number
            local function add(a, b) end

            add(1)
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val errors = snapshot.getErrors()
        assertFalse("Should have arity error", errors.isEmpty())
        assertTrue("Error should mention too few arguments", errors.any { it.message.contains("Too few arguments") })
    }

    @Test
    fun testArityTooMany() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@param a number
            local function f(a) end

            f(1, 2)
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val errors = snapshot.getErrors()
        assertFalse("Should have arity error", errors.isEmpty())
        assertTrue("Error should mention too many arguments", errors.any { it.message.contains("Too many arguments") })
    }

    // =========================================================================
    // Optional and variadic parameters
    // =========================================================================

    @Test
    fun testArityOptionalOK() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@param a number
            ---@param b? number
            local function f(a, b) end

            f(1) -- OK
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertTrue("Should have no errors with optional parameter omitted, but got: ${snapshot.getErrors().map { it.message }}", snapshot.getErrors().isEmpty())
    }

    @Test
    fun testArityVarargOK() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local function f(a, ...) end

            f(1, 2, 3, 4) -- OK
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertTrue("Should have no errors with varargs", snapshot.getErrors().isEmpty())
    }

    // =========================================================================
    // Argument type checking
    // =========================================================================

    @Test
    fun testArgumentTypeMismatch() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@param a string
            local function f(a) end

            f(42)
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val errors = snapshot.getErrors()
        assertFalse("Should have type mismatch error", errors.isEmpty())
        assertTrue("Error should mention number is not assignable to string, but was: ${errors.firstOrNull()?.message}",
            errors.any { it.message.contains("number") && it.message.contains("string") })
    }

    // =========================================================================
    // Function type contravariance (parameter types are contravariant)
    // =========================================================================

    @Test
    fun testContravarianceSuccess() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@param callback fun(n: number)
            local function doWork(callback) end

            doWork(function(any_val) end) -- OK: any handles number
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertTrue("Should have no errors for compatible function param, but got: ${snapshot.getErrors().map { it.message }}", snapshot.getErrors().isEmpty())
    }

    @Test
    fun testContravarianceFailure() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@param callback fun(s: string)
            local function doWork(callback) end

            ---@param n number
            local function my_cb(n) end

            doWork(my_cb) -- Error: callback expects string, but my_cb expects number
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val errors = snapshot.getErrors()
        assertFalse("Should have type mismatch error for function param", errors.isEmpty())
    }
}
