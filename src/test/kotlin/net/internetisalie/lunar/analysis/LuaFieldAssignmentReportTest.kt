package net.internetisalie.lunar.analysis

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * BUG-75. Assigning a wrong value to an annotated class field is reported on that assignment only,
 * never on the earlier valid one, and a `{}` assigned to a field of class type is not a missing-field report.
 */
class LuaFieldAssignmentReportTest : BasePlatformTestCase() {
    /** One `line:message` per reported finding (hypotheses are not reports), lines 1-based. */
    private fun findings(source: String): List<String> {
        var result: List<String> = emptyList()
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText("use.lua", source)
                val document = myFixture.editor.document
                result =
                    LuaTypesSnapshot
                        .forFile(myFixture.file)
                        .getErrors()
                        .filter { it.severity != ErrorSeverity.HYPOTHESIS }
                        .map { "${document.getLineNumber(it.element.textRange.startOffset) + 1}:${it.message}" }
                        .distinct()
            }
        }
        return result
    }

    private fun linesOf(source: String): Set<Int> = findings(source).map { it.substringBefore(':').toInt() }.toSet()

    fun testWrongFieldValueIsReportedOnItsOwnLineOnly() {
        val source =
            """
            ---@class A
            local m

            ---@type integer
            m.x = 1

            m.x = true
            """.trimIndent()
        assertEquals(setOf(7), linesOf(source))
    }

    fun testWrongMethodSelfFieldValueIsReportedOnItsOwnLineOnly() {
        val source =
            """
            ---@class A
            local mt

            ---@type integer
            mt.x = 1

            function mt:init()
                self.x = true
            end
            """.trimIndent()
        assertEquals(setOf(8), linesOf(source))
    }

    fun testWrongTableFieldValueIsReportedOnItsOwnLineOnly() {
        val source =
            """
            ---@class A
            local m

            ---@type number
            m.x = 1

            m.x = {}
            """.trimIndent()
        assertEquals(setOf(7), linesOf(source))
    }

    fun testEmptyTableAssignedToClassTypedFieldIsSilent() {
        val source =
            """
            ---@class A
            ---@field x A

            ---@type A
            local t

            t.x = {}
            """.trimIndent()
        assertEquals(emptyList<String>(), findings(source))
    }

    fun testEmptyTableAssignedToClassTypedLocalStillMissesItsFields() {
        val source =
            """
            ---@class A
            ---@field x integer

            ---@type A
            local t = {}
            """.trimIndent()
        assertEquals(listOf("5:Missing required field 'x'"), findings(source))
    }
}
