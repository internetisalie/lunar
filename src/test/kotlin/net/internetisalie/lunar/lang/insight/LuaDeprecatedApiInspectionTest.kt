package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaDeprecatedApiInspection
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaDeprecatedApiInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaDeprecatedApiInspection())
    }

    private fun deprecatedWarnings(text: String): List<String> {
        myFixture.configureByText("test.lua", text)
        return myFixture.doHighlighting()
            .filter { it.description?.contains("Deprecated API") == true }
            .map { it.description }
    }

    private fun assertNoDeprecated(text: String) {
        val warnings = deprecatedWarnings(text)
        assertTrue("Expected no deprecated warnings but found: $warnings", warnings.isEmpty())
    }

    private fun assertDeprecated(text: String, expectedMessage: String) {
        val warnings = deprecatedWarnings(text)
        assertEquals("Warnings: $warnings", 1, warnings.size)
        assertEquals(expectedMessage, warnings[0])
    }

    @Test
    fun testDeprecatedFunctionNoDescription() {
        assertDeprecated(
            """
            ---@deprecated
            local function old() end
            old()
            """.trimIndent(),
            "Deprecated API"
        )
    }

    @Test
    fun testDeprecatedFunctionWithDescription() {
        assertDeprecated(
            """
            ---@deprecated Use newFunc instead
            local function old() end
            old()
            """.trimIndent(),
            "Deprecated API: Use newFunc instead"
        )
    }

    @Test
    fun testDeprecatedLocalVariable() {
        assertDeprecated(
            """
            ---@deprecated
            local oldVar = 10
            print(oldVar)
            """.trimIndent(),
            "Deprecated API"
        )
    }

    @Test
    fun testNormalFunctionNotFlagged() {
        assertNoDeprecated(
            """
            local function normal() end
            normal()
            """.trimIndent()
        )
    }

    @Test
    fun testRecursiveCallFlagged() {
        assertDeprecated(
            """
            ---@deprecated
            local function old()
                old()
            end
            """.trimIndent(),
            "Deprecated API"
        )
    }
}
