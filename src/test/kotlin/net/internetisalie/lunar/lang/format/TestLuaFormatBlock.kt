package net.internetisalie.lunar.lang.format

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.LuaLanguage
import org.junit.jupiter.api.Disabled
import kotlin.test.Test

class TestLuaFormatBlock : BaseDocumentTest() {
    fun reformatText(fn: (CodeStyleSettings) -> Unit) {
        WriteCommandAction.writeCommandAction(myFixture.project).run<RuntimeException?> {
            val settings = CodeStyle.getSettings(myFixture.project)
            fn(settings)

            CodeStyleManager.getInstance(myFixture.project).reformatText(
                myFixture.file,
                listOf(myFixture.file.textRange),
            )
        }
    }

    @Test
    fun testDoBlock() {
        myFixture.configureByText(
            LuaFileType,
            """
             do
            print ""
             end
            """.trimIndent(),
        )

        reformatText {}

        myFixture.checkResult(
            """
            do
                print ""
            end
            """.trimIndent() + "\n",
        )
    }

    @Test
    fun testWhileBlock() {
        myFixture.configureByText(
            LuaFileType,
            """
             while true do
            print ""
             end
            """.trimIndent(),
        )

        reformatText {}

        myFixture.checkResult(
            """
            while true do
                print ""
            end
            """.trimIndent() + "\n",
        )
    }

    @Test
    fun testRepeatBlock() {
        myFixture.configureByText(
            LuaFileType,
            """
             repeat
            print ""
             until false
            """.trimIndent(),
        )

        reformatText {}

        myFixture.checkResult(
            """
            repeat
                print ""
            until false
            """.trimIndent() + "\n",
        )
    }

    @Test
    fun testIfBlock() {
        myFixture.configureByText(
            LuaFileType,
            """
             if false then
            print "1"
             elseif true then
            print "2"
             elseif false then
             else
            print "3"
             end
            """.trimIndent(),
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
            """.trimIndent() + "\n",
        )
    }

    @Test
    fun testNumericForBlock() {
        myFixture.configureByText(
            LuaFileType,
            """
             for a = 1, 2, 3 do
            print ""
             end
            """.trimIndent(),
        )

        reformatText {}

        myFixture.checkResult(
            """
            for a = 1, 2, 3 do
                print ""
            end
            """.trimIndent() + "\n",
        )
    }

    @Test
    fun testGenericForBlock() {
        myFixture.configureByText(
            LuaFileType,
            """
             for a in 1, 2, 3 do
            print ""
             end
            """.trimIndent(),
        )

        reformatText {}

        myFixture.checkResult(
            """
            for a in 1, 2, 3 do
                print ""
            end
            """.trimIndent() + "\n",
        )
    }

    @Test
    fun testFunctionBlock() {
        myFixture.configureByText(
            LuaFileType,
            """
            function test(a, 
            b, 
            c)
            print ""
            end
            """.trimIndent(),
        )

        reformatText {}

        myFixture.checkResult(
            """
            function test(a,
                          b,
                          c)
                print ""
            end
            """.trimIndent() + "\n",
        )
    }

    // Production yields `print(a,\n      b,\n      c)` (newline after `(` removed, args aligned
    // under the first), not the continuation-indented shape below. Un-ignore when BUG-482 is fixed.
    @Test
    @Disabled("BUG-482: a multi-line argument list is joined onto the call line instead of continuation-indented")
    fun testArgs() {
        myFixture.configureByText(
            LuaFileType,
            """
            print(
            a,
            b,
            c
            )
            """.trimIndent(),
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
            """.trimIndent() + "\n",
        )
    }

    /**
     * PINS KNOWN-DEFECTIVE BEHAVIOUR (BUG-484, FORMAT-01-22): `::start::` should sit at the block
     * indent; `Indent.getAbsoluteLabelIndent()` flushes it to column 0. When BUG-484 is fixed this
     * expectation is meant to change — the red here is then the fix, not a regression.
     */
    @Test
    fun `testLabel PINS BUG-484 label flushed to column 0`() {
        myFixture.configureByText(
            LuaFileType,
            """
            if restart then 
              ::start::
              goto start   
            end
            """.trimIndent(),
        )

        reformatText {}

        myFixture.checkResult(
            """
            if restart then
            ::start::
                goto start
            end
            """.trimIndent() + "\n",
        )
    }

    @Test
    fun testUnaryOperatorSpacing() {
        // Keyword `not` keeps a single space before its operand; symbolic unary
        // operators (`-`, `#`) stay tight. Regression guard for the UN_OP spacing
        // rule that previously tested the operand instead of the operator,
        // collapsing `not b` into the distinct identifier `notb`.
        myFixture.configureByText(
            LuaFileType,
            """
            local a = not b
            local c = -d
            local e = #f
            """.trimIndent(),
        )

        reformatText {}

        myFixture.checkResult(
            """
            local a = not b
            local c = -d
            local e = #f
            """.trimIndent() + "\n",
        )
    }

    @Test
    fun testVarList() {
        myFixture.configureByText(
            LuaFileType,
            """
            a,
            b,
            c = 1
            """.trimIndent(),
        )

        reformatText {}

        myFixture.checkResult(
            """
            a,
                    b,
                    c = 1
            """.trimIndent() + "\n",
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

    // Production leaves `b`, `c` at column 0 and indents the values only 4. Un-ignore when BUG-483 is fixed.
    @Test
    @Disabled("BUG-483: a multi-line local declaration gets no continuation indent on its names")
    fun testLocalVarDecl() {
        myFixture.configureByText(
            LuaFileType,
            """
            local a,
            b,
            c = 1,
            2,
            3
            """.trimIndent(),
        )

        reformatText {}

        myFixture.checkResult(
            """
            local a,
                  b,
                  c = 1,
                      2,
                      3
            """.trimIndent() + "\n",
        )
    }
}
