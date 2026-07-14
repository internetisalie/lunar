package net.internetisalie.lunar.lang.types

import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaIndexExpr
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REDIS-04 §3.1b: array-subscript element inference in [net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor].
 *
 * A bracket subscript `receiver[index]` whose receiver's value type is an `Array(T)` (directly or as
 * a union member) now infers the element type `T`; non-array receivers stay `Undefined` (regression
 * contract §3.1c). These cases are target-independent (they isolate §3.1b from stub seeding §3.1a).
 */
@RunWith(JUnit4::class)
class ArraySubscriptTypeTest : IndexedBasePlatformTestCase() {

    @Test
    fun testArraySubscriptInfersElementType_TC_IDX_1() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@type string[]
            local arr = {}
            local x = arr[1]
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val subscript = PsiTreeUtil.findChildrenOfType(file, LuaIndexExpr::class.java)
            .first { it.expr != null }
        assertEquals("arr[1] element should infer as string", "string", snapshot.getValueType(subscript).displayName())
    }

    @Test
    fun testNonArrayBracketStaysUndefined_TC_IDX_2() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local t = {}
            local y = t[1]
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val subscript = PsiTreeUtil.findChildrenOfType(file, LuaIndexExpr::class.java)
            .first { it.expr != null }
        assertEquals("non-array bracket access must stay undefined", "undefined", snapshot.getValueType(subscript).displayName())
    }

    @Test
    fun testDottedAccessUnchanged_TC_IDX_3() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local a = { b = "hi" }
            local m = a.b
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val dotted = PsiTreeUtil.findChildrenOfType(file, LuaIndexExpr::class.java)
            .first { it.nameRef != null }
        assertEquals("dotted member access must remain string", "string", snapshot.getValueType(dotted).displayName())
        assertTrue(
            "dotted access must not introduce type errors: ${snapshot.getErrors().map { it.message }}",
            snapshot.getErrors().isEmpty(),
        )
    }

    @Test
    fun testLengthOverArrayHasNoAssignabilityError() {
        // Differential isolation of the §3.1b `#`-operand sub-fix: adding `#arr` must NOT introduce
        // any new error vs the same declaration without it. (The `{}`-vs-`string[]` assignability
        // note from `local arr = {}` is a pre-existing baseline, unrelated to `#`.)
        val baseline = myFixture.configureByText(
            "baseline.lua",
            """
            ---@param arr string[]
            local function f(arr)
            end
            """.trimIndent(),
        )
        val baselineErrors = LuaTypesSnapshot.forFile(baseline).getErrors().size

        val withLength = myFixture.configureByText(
            "withlen.lua",
            """
            ---@param arr string[]
            local function f(arr)
                local n = #arr
                return n
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(withLength)
        val lengthRef = PsiTreeUtil.findChildrenOfType(withLength, LuaNameRef::class.java).first { it.text == "n" }
        assertEquals("#arr must infer number", "number", snapshot.getValueType(lengthRef).displayName())
        assertEquals(
            "#arr over string[] must add no assignability error vs baseline: ${snapshot.getErrors().map { it.message }}",
            baselineErrors,
            snapshot.getErrors().size,
        )
        assertTrue(
            "#arr over string[] must not emit a length-operand not-assignable error: ${snapshot.getErrors().map { it.message }}",
            snapshot.getErrors().none { it.message.contains("string[]") && it.message.contains("string | table") },
        )
    }
}
