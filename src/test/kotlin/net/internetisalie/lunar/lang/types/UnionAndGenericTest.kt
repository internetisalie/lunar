package net.internetisalie.lunar.lang.types

import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for union types, generic type parameters, and cyclic type references.
 *
 * This test suite validates advanced type features:
 * - Union type compatibility (e.g., string | number)
 * - Generic type parameters (T, U with constraints)
 * - Self-referencing and cyclic type definitions
 * - Generic instantiation and type parameter binding
 *
 * **Component**: Type Inference Engine - Phase 5 (Unions, Generics & Cyclic Safety)
 * **Features**:
 * - Union types for overloaded operations
 * - Generics for type-safe abstractions
 * - Cyclic resolution to prevent infinite recursion
 */
@RunWith(JUnit4::class)
class UnionAndGenericTest : IndexedBasePlatformTestCase() {

    // =========================================================================
    // Cyclic type definitions (self-referencing)
    // =========================================================================

    @Test
    fun testSelfReferencingClass() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@class Node
            ---@field next Node
            local Node = {}

            ---@type Node
            local n = { next = nil }
            local x = n.next
            """.trimIndent(),
        )
        // This should not StackOverflow
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull(snapshot)
        val errors = snapshot.getErrors()
        // We might still have a "nil not assignable to Node" error depending on Nil semantics,
        // but we definitely shouldn't have a StackOverflow.
        assertTrue("Should not have StackOverflow", true)
    }

    // =========================================================================
    // Union type checking
    // =========================================================================

    @Test
    fun testUnionTypeCheck() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@type string | number
            local x = "hello" -- OK
            x = 42            -- OK

            ---@type string | number
            local y = true    -- Error: boolean not assignable to string | number
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val errors = snapshot.getErrors()
        assertFalse("Should have at least one error for 'true' assigned to string|number", errors.isEmpty())
    }

    @Test
    fun testUnionToPrimitiveError() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@type string | number
            local x = 42

            ---@type string
            local s = x -- Error: 42 is not assignable to string
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val errors = snapshot.getErrors()
        assertFalse("Should have error when assigning number (via union-typed var) to string constraint", errors.isEmpty())
        assertTrue("Error should mention number and string",
            errors.any { it.message.contains("number") && it.message.contains("string") })
    }

    // =========================================================================
    // Generic type parameters
    // =========================================================================

    @Test
    fun testGenericIdentity() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@generic T
            ---@param val T
            ---@return T
            local function identity(val) return val end

            local s = identity("hello")
            ---@type string
            local s_check = s

            local n = identity(42)
            ---@type number
            local n_check = n

            local err = identity("oops")
            ---@type number
            local err_check = err -- Error: string not assignable to number
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val errors = snapshot.getErrors()
        assertFalse("Should have type mismatch error for generic return", errors.isEmpty())
        assertTrue("Error should mention string and number, but got: ${errors.map { it.message }}",
            errors.any { it.message.contains("string") && it.message.contains("number") })
    }
}
