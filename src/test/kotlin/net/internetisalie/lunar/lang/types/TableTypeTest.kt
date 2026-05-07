package net.internetisalie.lunar.lang.types

import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for table type checking and member access validation.
 *
 * This test suite validates table type handling:
 * - Table constructor type inference
 * - Table member access and type checking
 * - Type compatibility in table member assignments
 *
 * **Component**: Type Inference Engine - Phase 4 (Table Types)
 * **Feature**: Lua tables are flexible; this ensures member access is properly typed.
 */
@RunWith(JUnit4::class)
class TableTypeTest : IndexedBasePlatformTestCase() {

    // =========================================================================
    // Table constructor type inference
    // =========================================================================

    @Test
    fun testTableConstructorBasic() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local t = { a = 1, b = "hi" }

            ---@type number
            local n = t.a -- OK

            ---@type string
            local s = t.b -- OK
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertTrue("Should have no errors in table access, but got: ${snapshot.getErrors().map { it.message }}", snapshot.getErrors().isEmpty())
    }

    @Test
    fun testTableConstructorMismatch() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local t = { a = 1 }

            ---@type string
            local s = t.a -- Error: t.a is number
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val errors = snapshot.getErrors()
        assertFalse("Should have type mismatch error", errors.isEmpty())
        assertTrue("Error should mention number is not assignable to string",
            errors.any { it.message.contains("number") && it.message.contains("string") })
    }
}
