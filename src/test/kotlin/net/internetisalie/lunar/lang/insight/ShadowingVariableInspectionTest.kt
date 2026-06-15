package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaShadowingVariableInspection
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ShadowingVariableInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaShadowingVariableInspection())
    }

    private fun shadowingWarnings(text: String): List<String> {
        myFixture.configureByText("test.lua", text)
        return myFixture.doHighlighting()
            .filter { it.description?.startsWith("Shadowing variable") == true }
            .map { it.description }
    }

    private fun assertNoShadowing(text: String) {
        val warnings = shadowingWarnings(text)
        assertTrue("Expected no shadowing warnings but found: $warnings", warnings.isEmpty())
    }

    private fun assertShadowing(text: String, vararg names: String) {
        val warnings = shadowingWarnings(text)
        assertEquals("Warnings: $warnings", names.size, warnings.size)
        for (name in names) {
            assertTrue(
                "Expected warning for '$name' but found: $warnings",
                warnings.contains("Shadowing variable '$name'"),
            )
        }
    }

    @Test
    fun testSimpleShadowing() {
        assertShadowing(
            """
            local x = 1
            do
                local x = 2
            end
            """.trimIndent(),
            "x"
        )
    }

    @Test
    fun testNoShadowingSameLevel() {
        assertNoShadowing(
            """
            local x = 1
            local y = 2
            """.trimIndent()
        )
    }

    @Test
    fun testNoShadowingSiblingScopes() {
        assertNoShadowing(
            """
            do
                local x = 1
            end
            do
                local x = 2
            end
            """.trimIndent()
        )
    }

    @Test
    fun testRedeclarationSameScope() {
        assertShadowing(
            """
            local x = 1
            local x = 2
            """.trimIndent(),
            "x"
        )
    }

    @Test
    fun testFunctionParameterShadowsLocal() {
        assertShadowing(
            """
            local x = 1
            local function test(x)
            end
            """.trimIndent(),
            "x"
        )
    }

    @Test
    fun testLocalShadowsFunctionParameter() {
        assertShadowing(
            """
            local function test(x)
                local x = 2
            end
            """.trimIndent(),
            "x"
        )
    }

    @Test
    fun testLoopVariableShadowsLocal() {
        assertShadowing(
            """
            local x = 1
            for x = 1, 10 do
            end
            """.trimIndent(),
            "x"
        )
    }

    @Test
    fun testLocalShadowsLoopVariable() {
        assertShadowing(
            """
            for x = 1, 10 do
                local x = 2
            end
            """.trimIndent(),
            "x"
        )
    }

    @Test
    fun testGenericLoopVariableShadowsLocal() {
        assertShadowing(
            """
            local x = 1
            for x in pairs({}) do
            end
            """.trimIndent(),
            "x"
        )
    }

    @Test
    fun testLocalShadowsGenericLoopVariable() {
        assertShadowing(
            """
            for x in pairs({}) do
                local x = 2
            end
            """.trimIndent(),
            "x"
        )
    }

    @Test
    fun testLocalFunctionShadowsLocal() {
        assertShadowing(
            """
            local x = 1
            local function x()
            end
            """.trimIndent(),
            "x"
        )
    }

    @Test
    fun testLocalShadowsLocalFunction() {
        assertShadowing(
            """
            local function x()
            end
            local x = 1
            """.trimIndent(),
            "x"
        )
    }

    @Test
    fun testParameterNoShadowing() {
        assertNoShadowing(
            """
            local function test(a, b)
            end
            """.trimIndent()
        )
    }

    @Test
    fun testDuplicateParameters() {
        assertShadowing(
            """
            local function test(a, a)
            end
            """.trimIndent(),
            "a"
        )
    }

    @Test
    fun testWildcardIgnored() {
        assertNoShadowing(
            """
            local _ = 1
            do
                local _ = 2
            end
            """.trimIndent()
        )
    }
}
