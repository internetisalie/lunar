package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaSuspiciousConcatenationInspection
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaSuspiciousConcatenationInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaSuspiciousConcatenationInspection())
    }

    private fun concatWarnings(text: String): List<String> {
        myFixture.configureByText("test.lua", text)
        return myFixture.doHighlighting()
            .filter { it.description?.startsWith("Suspicious concatenation") == true }
            .map { it.description!! }
    }

    private fun assertNoWarning(text: String) {
        val warnings = concatWarnings(text)
        assertTrue("Expected no suspicious-concatenation warnings but found: $warnings", warnings.isEmpty())
    }

    private fun assertOneWarning(text: String, expectedMessage: String) {
        val warnings = concatWarnings(text)
        assertEquals("Expected exactly one warning but got: $warnings", 1, warnings.size)
        assertEquals(expectedMessage, warnings[0])
    }

    /** TC-1: table operand on the right (INSP-07-01, INSP-07-04) */
    @Test
    fun testTableOperandWarns() {
        assertOneWarning(
            """
            local t = {}
            local s = "hello " .. t
            """.trimIndent(),
            "Suspicious concatenation: operand of type '{ ... }' cannot be concatenated",
        )
    }

    /** TC-2: boolean operand on the left (INSP-07-01, INSP-07-04) */
    @Test
    fun testBooleanOperandWarns() {
        assertOneWarning(
            """
            local b = true
            local s = b .. " yes"
            """.trimIndent(),
            "Suspicious concatenation: operand of type 'boolean' cannot be concatenated",
        )
    }

    /** TC-3: string and number operands — no warning (INSP-07-03) */
    @Test
    fun testStringAndNumberNoWarning() {
        assertNoWarning(
            """
            local n = 1
            local s = "x" .. n .. "y"
            """.trimIndent(),
        )
    }

    /** TC-4: un-inferable operand (any/undefined) — no warning (INSP-07-03) */
    @Test
    fun testUnknownGlobalNoWarning() {
        assertNoWarning(
            """
            local s = "x" .. unknownGlobal
            """.trimIndent(),
        )
    }

    /** TC-5: union with a concatenable member (string|nil) — no warning (INSP-07-02) */
    @Test
    fun testUnionWithConcatenableMemberNoWarning() {
        assertNoWarning(
            """
            ---@type string|nil
            local maybe = nil
            local s = "x" .. maybe
            """.trimIndent(),
        )
    }

    /** TC-6: union with all non-concatenable members (boolean|nil) — warns (INSP-07-02, INSP-07-01) */
    @Test
    fun testUnionAllNonConcatenableWarns() {
        assertOneWarning(
            """
            ---@type boolean|nil
            local flag = nil
            local s = "x" .. flag
            """.trimIndent(),
            // The engine canonicalizes `---@type boolean|nil` merged with initializer `nil`;
            // the actual display order from LuaTypeAlgebra is "nil | boolean" (engine-verified).
            "Suspicious concatenation: operand of type 'nil | boolean' cannot be concatenated",
        )
    }

    /** TC-7: function operand — warns (INSP-07-01) */
    @Test
    fun testFunctionOperandWarns() {
        assertOneWarning(
            """
            local f = function() end
            local s = "hello" .. f
            """.trimIndent(),
            "Suspicious concatenation: operand of type 'fun()' cannot be concatenated",
        )
    }

    /** TC-8: nil operand — warns (INSP-07-01) */
    @Test
    fun testNilOperandWarns() {
        assertOneWarning(
            """
            local n = nil
            local s = "hello" .. n
            """.trimIndent(),
            "Suspicious concatenation: operand of type 'nil' cannot be concatenated",
        )
    }
}
