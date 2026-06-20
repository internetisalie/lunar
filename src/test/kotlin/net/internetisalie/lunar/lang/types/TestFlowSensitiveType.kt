package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaIfStatement
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-08 (Flow-Sensitive Types) tests. Each case configures a Lua file containing a type-guard
 * `if`/`elseif`/`else`, builds a [LuaTypesSnapshot], and asserts the narrowed type of `x` at a
 * `print(x)` reference inside a specific branch. Covers all 13 test cases from requirements.md.
 */
@RunWith(JUnit4::class)
class TestFlowSensitiveType : BasePlatformTestCase() {

    private fun typeOfXInBranch(source: String, branchIndex: Int): LuaGraphType {
        val file = myFixture.configureByText("test.lua", source)
        val snapshot = LuaTypesSnapshot.forFile(file)
        val ifStatement = PsiTreeUtil.findChildOfType(file, LuaIfStatement::class.java)
            ?: error("No if statement found")
        val block = ifStatement.getBlockList()[branchIndex]
        val nameRef = nameRefNamed(block, "x") ?: error("No reference to x in branch $branchIndex")
        return snapshot.getValueType(nameRef)
    }

    private fun nameRefNamed(root: PsiElement, name: String): LuaNameRef? =
        PsiTreeUtil.findChildrenOfType(root, LuaNameRef::class.java).firstOrNull { it.text == name }

    // TC-1: type() equality, then branch -> string
    @Test
    fun testTypeofEqualityThenNarrowsToString() {
        val type = typeOfXInBranch(
            """
            ---@type string|number
            local x
            if type(x) == "string" then print(x) end
            """.trimIndent(),
            0,
        )
        assertEquals("string", type.displayName())
    }

    // TC-2: type() equality, else branch -> number (original minus string)
    @Test
    fun testTypeofEqualityElseNarrowsToNumber() {
        val type = typeOfXInBranch(
            """
            ---@type string|number
            local x
            if type(x) == "string" then print(x) else print(x) end
            """.trimIndent(),
            1,
        )
        assertEquals("number", type.displayName())
    }

    // TC-3: type() inequality with "nil", then branch -> string|number (nil removed)
    @Test
    fun testTypeofInequalityNilThenRemovesNil() {
        val type = typeOfXInBranch(
            """
            ---@type string|number|nil
            local x
            if type(x) ~= "nil" then print(x) end
            """.trimIndent(),
            0,
        )
        assertTrue("Expected a union, got ${type.displayName()}", type is LuaGraphType.Union)
        val members = (type as LuaGraphType.Union).types
        assertTrue("string missing", members.contains(LuaGraphType.String))
        assertTrue("number missing", members.contains(LuaGraphType.Number))
        assertFalse("nil should be removed", members.contains(LuaGraphType.Nil))
    }

    // TC-4: type() inequality with "nil", else branch -> nil
    @Test
    fun testTypeofInequalityNilElseIsNil() {
        val type = typeOfXInBranch(
            """
            ---@type string|number|nil
            local x
            if type(x) ~= "nil" then print(x) else print(x) end
            """.trimIndent(),
            1,
        )
        assertEquals(LuaGraphType.Nil, type)
    }

    // TC-5: nil equality, then branch -> nil
    @Test
    fun testNilEqualityThenIsNil() {
        val type = typeOfXInBranch(
            """
            ---@type string|nil
            local x
            if x == nil then print(x) end
            """.trimIndent(),
            0,
        )
        assertEquals(LuaGraphType.Nil, type)
    }

    // TC-6: nil equality, else branch -> string
    @Test
    fun testNilEqualityElseIsString() {
        val type = typeOfXInBranch(
            """
            ---@type string|nil
            local x
            if x == nil then print(x) else print(x) end
            """.trimIndent(),
            1,
        )
        assertEquals(LuaGraphType.String, type)
    }

    // TC-7: nil inequality, then branch -> string (nil removed)
    @Test
    fun testNilInequalityThenIsString() {
        val type = typeOfXInBranch(
            """
            ---@type string|nil
            local x
            if x ~= nil then print(x) end
            """.trimIndent(),
            0,
        )
        assertEquals(LuaGraphType.String, type)
    }

    // TC-8: nil inequality, else branch -> nil
    @Test
    fun testNilInequalityElseIsNil() {
        val type = typeOfXInBranch(
            """
            ---@type string|nil
            local x
            if x ~= nil then print(x) else print(x) end
            """.trimIndent(),
            1,
        )
        assertEquals(LuaGraphType.Nil, type)
    }

    // TC-9 (DR-02): elseif chain, first elseif branch -> number
    @Test
    fun testElseifChainSecondBranchIsNumber() {
        val type = typeOfXInBranch(
            """
            ---@type string|number
            local x
            if type(x) == "string" then
                print(x)
            elseif type(x) == "number" then
                print(x)
            end
            """.trimIndent(),
            1,
        )
        assertEquals("number", type.displayName())
    }

    // TC-10 (DR-02): elseif chain with else, else branch -> boolean
    @Test
    fun testElseifChainElseBranchIsBoolean() {
        val type = typeOfXInBranch(
            """
            ---@type string|number|boolean
            local x
            if type(x) == "string" then
                print(x)
            elseif type(x) == "number" then
                print(x)
            else
                print(x)
            end
            """.trimIndent(),
            2,
        )
        assertEquals(LuaGraphType.Boolean, type)
    }

    // TC-11: uninferred local narrowed via type() guard -> string
    @Test
    fun testUninferredLocalNarrowsToString() {
        val type = typeOfXInBranch(
            """
            local x = "hello"
            if type(x) == "string" then print(x) end
            """.trimIndent(),
            0,
        )
        assertEquals("string", type.displayName())
    }

    // TC-12: type() == "table" (not a member), else branch keeps original string|number
    @Test
    fun testTableGuardElseKeepsOriginalUnion() {
        val type = typeOfXInBranch(
            """
            ---@type string|number
            local x
            if type(x) == "table" then print(x) else print(x) end
            """.trimIndent(),
            1,
        )
        assertTrue("Expected a union, got ${type.displayName()}", type is LuaGraphType.Union)
        val members = (type as LuaGraphType.Union).types
        assertTrue("string missing", members.contains(LuaGraphType.String))
        assertTrue("number missing", members.contains(LuaGraphType.Number))
    }

    // TC-13: non-guard conditional leaves x un-narrowed (string|number)
    @Test
    fun testNonGuardConditionalDoesNotNarrow() {
        val type = typeOfXInBranch(
            """
            ---@type string|number
            local x
            if x > 5 then print(x) end
            """.trimIndent(),
            0,
        )
        assertTrue("Expected a union, got ${type.displayName()}", type is LuaGraphType.Union)
        val members = (type as LuaGraphType.Union).types
        assertTrue("string missing", members.contains(LuaGraphType.String))
        assertTrue("number missing", members.contains(LuaGraphType.Number))
    }

    // Acceptance criterion 2: narrowing must not leak past `end`.
    @Test
    fun testNarrowingDoesNotLeakAfterBlock() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@type string|number
            local x
            if type(x) == "string" then print(x) end
            local y = x
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val afterRef = PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java)
            .lastOrNull { it.text == "x" } ?: error("No trailing reference to x")
        val type = snapshot.getValueType(afterRef)
        assertTrue("Expected a union after the block, got ${type.displayName()}", type is LuaGraphType.Union)
        val members = (type as LuaGraphType.Union).types
        assertTrue("string missing", members.contains(LuaGraphType.String))
        assertTrue("number missing", members.contains(LuaGraphType.Number))
    }
}
