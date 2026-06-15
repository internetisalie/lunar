package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaGlobalCreationInspection
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaGlobalCreationInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaGlobalCreationInspection())
    }

    private fun globalCreationWarnings(text: String): List<String> {
        myFixture.configureByText("test.lua", text)
        return myFixture.doHighlighting()
            .filter { it.description?.startsWith("Global creation") == true }
            .map { it.description }
    }

    private fun assertNoGlobalCreation(text: String) {
        val warnings = globalCreationWarnings(text)
        assertTrue("Expected no global creation warnings but found: $warnings", warnings.isEmpty())
    }

    private fun assertGlobalCreation(text: String, vararg names: String) {
        val warnings = globalCreationWarnings(text)
        assertEquals("Warnings: $warnings", names.size, warnings.size)
        for (name in names) {
            assertTrue(
                "Expected warning for '$name' but found: $warnings",
                warnings.contains("Global creation '$name'"),
            )
        }
    }

    @Test
    fun testImplicitGlobalFlagged() {
        assertGlobalCreation("myGlobal = 1", "myGlobal")
    }

    @Test
    fun testQuickFixMakeLocal() {
        myFixture.configureByText("test.lua", "<caret>myGlobal = 1")
        val intention = myFixture.findSingleIntention("Make Local")
        myFixture.launchAction(intention)
        myFixture.checkResult("local myGlobal = 1")
    }

    @Test
    fun testSecondAssignmentNotFlagged() {
        assertGlobalCreation(
            """
            g = 1
            g = 2
            """.trimIndent(),
            "g"
        )
    }

    @Test
    fun testLocalAssignmentNotFlagged() {
        assertNoGlobalCreation(
            """
            local x = 1
            x = 2
            """.trimIndent()
        )
    }

    @Test
    fun testStandardGlobalsNotFlagged() {
        assertNoGlobalCreation("print = 1")
    }

    @Test
    fun testAdditionalGlobalsNotFlagged() {
        LuaProjectSettings.getInstance(project).state.additionalGlobals.add("customGlobal")
        try {
            assertNoGlobalCreation("customGlobal = 1")
        } finally {
            LuaProjectSettings.getInstance(project).state.additionalGlobals.remove("customGlobal")
        }
    }

    @Test
    fun testCommentSuppression() {
        assertNoGlobalCreation(
            """
            ---@diagnostic disable-next-line: undefined-global
            myGlobal = 1
            """.trimIndent()
        )
    }

    @Test
    fun testMultipleAssignments() {
        assertGlobalCreation(
            """
            local x
            x, y = 1, 2
            """.trimIndent(),
            "y"
        )
    }
}
