package net.internetisalie.lunar.lang.format

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.LuaLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wave 7 formatting features: FORMAT-03 (blank lines), FORMAT-04 (wrapping),
 * FORMAT-05 (alignment), FORMAT-06 (comments). Each test drives the real reformat machinery
 * via [CodeStyleManager.reformatText] and asserts the user-visible result.
 */
class TestLuaFormattingWave7 : BaseDocumentTest() {

    private fun reformat(configure: (CodeStyleSettings) -> Unit = {}) {
        WriteCommandAction.writeCommandAction(myFixture.project).run<RuntimeException?> {
            val settings = CodeStyle.getSettings(myFixture.project)
            configure(settings)
            CodeStyleManager.getInstance(myFixture.project).reformatText(
                myFixture.file, listOf(myFixture.file.textRange)
            )
        }
    }

    private fun lua(settings: CodeStyleSettings): LuaCodeStyleSettings =
        settings.getCustomSettings(LuaCodeStyleSettings::class.java)

    private fun common(settings: CodeStyleSettings): CommonCodeStyleSettings =
        settings.getCommonSettings(LuaLanguage)

    // --- FORMAT-03 Blank line management ---------------------------------------------------

    @Test
    fun testFunctionSeparationHonorsSetting() {
        myFixture.configureByText(
            LuaFileType,
            """
            function a() end
            function b() end
            """.trimIndent()
        )

        reformat { common(it).BLANK_LINES_AROUND_METHOD = 2 }

        myFixture.checkResult(
            "function a() end\n\n\nfunction b() end\n"
        )
    }

    @Test
    fun testKeepMaxBlankLines() {
        myFixture.configureByText(
            LuaFileType,
            "do\nlocal x = 1\n\n\n\n\nlocal y = 2\nend"
        )

        reformat { common(it).KEEP_BLANK_LINES_IN_CODE = 1 }

        assertEquals(
            "do\n    local x = 1\n\n    local y = 2\nend\n",
            myFixture.file.text
        )
    }

    @Test
    fun testTrailingNewlineAdded() {
        myFixture.configureByText(LuaFileType, "local x = 1")
        reformat()
        myFixture.checkResult("local x = 1\n")
    }

    @Test
    fun testTrailingNewlineCollapsed() {
        myFixture.configureByText(LuaFileType, "local x = 1\n\n\n")
        reformat()
        myFixture.checkResult("local x = 1\n")
    }

    // --- FORMAT-04 Expression wrapping -----------------------------------------------------

    @Test
    fun testWrapArgumentsAlways() {
        myFixture.configureByText(LuaFileType, "f(alpha, beta, gamma)")

        reformat { lua(it).WRAP_ARGUMENTS = CommonCodeStyleSettings.WRAP_ALWAYS }

        // WRAP_ALWAYS puts every argument on its own line (the first may follow `f(`).
        val text = myFixture.file.text
        assertTrue(text.contains("alpha,\n"), "args should chop onto separate lines: $text")
        assertTrue(text.contains("beta,\n"), "args should chop onto separate lines: $text")
        assertTrue(text.trimEnd().endsWith("gamma)"), "last arg ends the call: $text")
    }

    @Test
    fun testWrapArgumentsDoNotWrap() {
        myFixture.configureByText(LuaFileType, "f(alpha, beta, gamma)")

        reformat {
            lua(it).WRAP_ARGUMENTS = CommonCodeStyleSettings.DO_NOT_WRAP
            common(it).RIGHT_MARGIN = 10
        }

        // Stays on one line despite the tiny margin.
        assertEquals("f(alpha, beta, gamma)", myFixture.file.text.trim())
    }

    @Test
    fun testWrapTableConstructorAlways() {
        myFixture.configureByText(LuaFileType, "local t = { a, b, c }")

        reformat { lua(it).WRAP_TABLE_CONSTRUCTOR = CommonCodeStyleSettings.WRAP_ALWAYS }

        val text = myFixture.file.text
        assertTrue(text.contains("a,\n"), "table fields should chop onto separate lines: $text")
        assertTrue(text.contains("b,\n"), "table fields should chop onto separate lines: $text")
    }

    // --- FORMAT-05 Alignment logic ---------------------------------------------------------

    @Test
    fun testAlignConsecutiveAssignments() {
        myFixture.configureByText(
            LuaFileType,
            "x = 1\nabc = 2\nyy = 3"
        )

        reformat { lua(it).ALIGN_CONSECUTIVE_ASSIGNMENTS = true }

        myFixture.checkResult("x   = 1\nabc = 2\nyy  = 3\n")
    }

    @Test
    fun testAlignmentBrokenByBlankLine() {
        myFixture.configureByText(
            LuaFileType,
            "x = 1\nabc = 2\n\nzz = 3\nq = 4"
        )

        reformat { lua(it).ALIGN_CONSECUTIVE_ASSIGNMENTS = true }

        myFixture.checkResult("x   = 1\nabc = 2\n\nzz = 3\nq  = 4\n")
    }

    @Test
    fun testAlignmentOffByDefault() {
        myFixture.configureByText(LuaFileType, "x = 1\nabc = 2")
        reformat()
        myFixture.checkResult("x = 1\nabc = 2\n")
    }

    @Test
    fun testAlignTableFields() {
        myFixture.configureByText(
            LuaFileType,
            "local t = {\na = 1,\nbb = 2,\nccc = 3,\n}"
        )

        reformat { lua(it).ALIGN_TABLE_FIELDS = true }

        myFixture.checkResult(
            "local t = {\n    a   = 1,\n    bb  = 2,\n    ccc = 3,\n}\n"
        )
    }

    // --- FORMAT-06 Comment formatting ------------------------------------------------------

    @Test
    fun testWrapLongComment() {
        myFixture.configureByText(
            LuaFileType,
            "-- alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu"
        )

        reformat {
            lua(it).WRAP_LONG_COMMENTS = true
            common(it).RIGHT_MARGIN = 30
        }

        val lines = myFixture.file.text.trimEnd().lines()
        assertTrue(lines.size > 1, "long comment should wrap: ${myFixture.file.text}")
        lines.forEach {
            assertTrue(it.startsWith("-- "), "each wrapped line keeps the comment prefix: '$it'")
            assertTrue(it.length <= 30, "each wrapped line fits the margin: '$it'")
        }
    }

    @Test
    fun testDocCommentNotWrapped() {
        val doc = "---@param verylongparametername SomeVeryLongTypeNameThatExceedsTheConfiguredRightMargin"
        myFixture.configureByText(LuaFileType, doc)

        reformat {
            lua(it).WRAP_LONG_COMMENTS = true
            common(it).RIGHT_MARGIN = 30
        }

        assertEquals(doc, myFixture.file.text.trim(), "LuaCATS doc comments must not be wrapped")
    }

    @Test
    fun testShortCommentNotWrapped() {
        myFixture.configureByText(LuaFileType, "-- short")
        reformat {
            lua(it).WRAP_LONG_COMMENTS = true
            common(it).RIGHT_MARGIN = 80
        }
        myFixture.checkResult("-- short\n")
    }
}
