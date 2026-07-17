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
import org.junit.Ignore

class TestLuaFormatBlock : BaseDocumentTest() {

    fun reformatText(fn: (CodeStyleSettings) -> Unit) {
        WriteCommandAction.writeCommandAction(myFixture.project).run<RuntimeException?> {
            val settings = CodeStyle.getSettings(myFixture.project)
            fn(settings)

            CodeStyleManager.getInstance(myFixture.project).reformatText(
                myFixture.file, listOf(myFixture.file.textRange)
            )
        }
    }

    @Test
    fun testDoBlock() {
        myFixture.configureByText(
            LuaFileType, """
             do
            print ""
             end
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            do
                print ""
            end
        """.trimIndent() + "\n"
        )
    }

    @Test
    fun testWhileBlock() {
        myFixture.configureByText(
            LuaFileType, """
             while true do
            print ""
             end
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            while true do
                print ""
            end
        """.trimIndent() + "\n"
        )
    }

    @Test
    fun testRepeatBlock() {
        myFixture.configureByText(
            LuaFileType, """
             repeat
            print ""
             until false
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            repeat
                print ""
            until false
        """.trimIndent() + "\n"
        )
    }

    @Test
    fun testIfBlock() {
        myFixture.configureByText(
            LuaFileType, """
             if false then
            print "1"
             elseif true then
            print "2"
             elseif false then
             else
            print "3"
             end
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            if false then
                print "1"
            elseif true then
                print "2"
            elseif false then
            else
                print "3"
            end
        """.trimIndent() + "\n"
        )
    }

    @Test
    fun testNumericForBlock() {
        myFixture.configureByText(
            LuaFileType, """
             for a = 1, 2, 3 do
            print ""
             end
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            for a = 1, 2, 3 do
                print ""
            end
        """.trimIndent() + "\n"
        )
    }

    @Test
    fun testGenericForBlock() {
        myFixture.configureByText(
            LuaFileType, """
             for a in 1, 2, 3 do
            print ""
             end
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            for a in 1, 2, 3 do
                print ""
            end
        """.trimIndent() + "\n"
        )
    }

    @Test
    fun testFunctionBlock() {
        myFixture.configureByText(
            LuaFileType, """
            function test(a, 
            b, 
            c)
            print ""
            end
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            function test(a,
                          b,
                          c)
                print ""
            end
        """.trimIndent() + "\n"
        )
    }

    @Ignore
    fun testArgs() {
        myFixture.configureByText(
            LuaFileType, """
            print(
            a,
            b,
            c
            )
        """.trimIndent()
        )

        reformatText { codeStyleSettings ->
            val indentOptions = codeStyleSettings.getIndentOptions(LuaFileType)
            indentOptions.CONTINUATION_INDENT_SIZE = 4
        }

        myFixture.checkResult(
            """
            print(
                a,
                b,
                c
            )
        """.trimIndent() + "\n"
        )
    }

    @Test
    fun testLabel() {
        myFixture.configureByText(
            LuaFileType, """
            if restart then 
              ::start::
              goto start   
            end
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            if restart then
            ::start::
                goto start
            end
        """.trimIndent() + "\n"
        )
    }

    @Test
    fun testUnaryOperatorSpacing() {
        // Keyword `not` keeps a single space before its operand; symbolic unary
        // operators (`-`, `#`) stay tight. Regression guard for the UN_OP spacing
        // rule that previously tested the operand instead of the operator,
        // collapsing `not b` into the distinct identifier `notb`.
        myFixture.configureByText(
            LuaFileType, """
            local a = not b
            local c = -d
            local e = #f
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            local a = not b
            local c = -d
            local e = #f
        """.trimIndent() + "\n"
        )
    }

    @Test
    fun testVarList() {
        myFixture.configureByText(
            LuaFileType, """
            a,
            b,
            c = 1
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            a,
                    b,
                    c = 1
        """.trimIndent() + "\n"
        )
    }

    // ── BUG-382: SPACE_WITHIN_BRACKETS must be authoritative ─────────────────────

    @Test
    fun `bracket spacing off produces no spaces inside brackets`() {
        myFixture.configureByText(LuaFileType, "local x = t[1]")
        reformatText { settings ->
            settings.getCommonSettings(LuaLanguage).SPACE_WITHIN_BRACKETS = false
        }
        myFixture.checkResult("local x = t[1]\n")
    }

    @Test
    fun `bracket spacing on produces spaces inside brackets`() {
        myFixture.configureByText(LuaFileType, "local x = t[1]")
        reformatText { settings ->
            settings.getCommonSettings(LuaLanguage).SPACE_WITHIN_BRACKETS = true
        }
        myFixture.checkResult("local x = t[ 1 ]\n")
    }

    @Ignore
    fun testLocalVarDecl() {
        myFixture.configureByText(
            LuaFileType, """
            local a,
            b,
            c = 1,
            2,
            3
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            local a,
                  b,
                  c = 1,
                      2,
                      3
        """.trimIndent() + "\n"
        )
    }
}
