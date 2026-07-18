package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaUnusedLocalInspection
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaUnusedLocalInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaUnusedLocalInspection())
    }

    private fun unusedWarnings(text: String): List<String> {
        myFixture.configureByText("test.lua", text)
        return myFixture.doHighlighting()
            .filter { it.description?.startsWith("Unused") == true }
            .map { it.description }
    }

    private fun assertNoUnused(text: String) {
        val warnings = unusedWarnings(text)
        assertTrue("Expected no unused warnings but found: $warnings", warnings.isEmpty())
    }

    private fun assertUnused(text: String, vararg expectedWarnings: String) {
        val warnings = unusedWarnings(text)
        assertEquals("Warnings count mismatch. Found: $warnings", expectedWarnings.size, warnings.size)
        for (expected in expectedWarnings) {
            assertTrue(
                "Expected warning '$expected' but found: $warnings",
                warnings.contains(expected),
            )
        }
    }

    @Test
    fun testUnusedLocalFlagged() {
        assertUnused(
            """
            local x = 10
            local y = 20
            """.trimIndent(),
            "Unused local variable 'x'",
            "Unused local variable 'y'"
        )
    }

    @Test
    fun testUsedLocalNotFlagged() {
        assertNoUnused(
            """
            local x = 10
            print(x)
            """.trimIndent()
        )
    }

    @Test
    fun testIgnoredVariableNotFlagged() {
        assertNoUnused(
            """
            local _ = 10
            for _, v in pairs({}) do print(v) end
            """.trimIndent()
        )
        // Variables starting with _ (but not exactly _) ARE flagged
        assertUnused(
            """
            local _x = 20
            """.trimIndent(),
            "Unused local variable '_x'"
        )
    }

    @Test
    fun testUnusedParameterFlagged() {
        val inspection = LuaUnusedLocalInspection().apply { checkParameters = true }
        myFixture.enableInspections(inspection)
        assertUnused(
            """
            function test(a, b)
                print(a)
            end
            """.trimIndent(),
            "Unused parameter 'b'"
        )
    }

    @Test
    fun testUnusedParameterNotFlaggedByDefault() {
        assertNoUnused(
            """
            function test(a, b)
                print(a)
            end
            """.trimIndent()
        )
    }

    @Test
    fun testCapturedByClosureNotFlagged() {
        // Resolution follows closure captures; a CFG-only approach would miss this read.
        assertNoUnused(
            """
            local function outer()
                local x = 1
                return function() return x end
            end
            print(outer())
            """.trimIndent()
        )
    }

    @Test
    fun testUnusedNumericForVariableFlagged() {
        assertUnused(
            """
            for i = 1, 10 do
                print("hi")
            end
            """.trimIndent(),
            "Unused local variable 'i'"
        )
    }

    @Test
    fun testUsedNumericForVariableNotFlagged() {
        assertNoUnused(
            """
            for i = 1, 10 do
                print(i)
            end
            """.trimIndent()
        )
    }

    /** TC-09 (#34): a local that is only ever *assigned* (never read) is flagged unused. */
    @Test
    fun testAssignedOnlyLocalFlagged() {
        assertUnused(
            """
            local flag
            flag = true
            """.trimIndent(),
            "Unused local variable 'flag'",
        )
    }

    /** TC-09 (#34): a simple write target does not count as a read that clears the warning. */
    @Test
    fun testWriteThenReadLocalNotFlagged() {
        assertNoUnused(
            """
            local flag
            flag = true
            print(flag)
            """.trimIndent(),
        )
    }

    /**
     * TC-10 (#69): a usage that resolves (even ambiguously across shadowed decls) must keep the
     * read-referenced declaration off the unused list. multiResolve(false) retains ambiguous
     * targets that the old resolve()==null path silently dropped.
     */
    @Test
    fun testAmbiguousUsageDoesNotFalselyFlag() {
        assertUnused(
            """
            local value = 1
            do
                local value = 2
                print(value)
            end
            print(value)
            """.trimIndent(),
        )
        // Both `value` decls are read (inner by the do-block print, outer by the trailing print);
        // neither may be flagged unused even though the name resolves across shadowing scopes.
    }

    @Test
    fun testShadowedVariable() {
        assertUnused(
            """
            local x = 1
            do
                local x = 2
                print(x)
            end
            """.trimIndent(),
            "Unused local variable 'x'" // The outer x is unused
        )
    }
}
