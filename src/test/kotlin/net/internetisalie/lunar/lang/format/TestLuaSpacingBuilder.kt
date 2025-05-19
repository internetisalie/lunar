package net.internetisalie.lunar.lang.format

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import kotlin.test.Test

class TestLuaSpacingBuilder : BaseDocumentTest() {
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
    fun testFunctionBlock_Newlines() {
        myFixture.configureByText(
            LuaFileType, """
            function test() end
            print ""
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            function test() end
            
            print ""
        """.trimIndent()
        )
    }

    @Test
    fun testAnonymousFunctionHeader() {
        myFixture.configureByText(
            LuaFileType, """
            local a = function  (
            
            ) end
        """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
            local a = function() end
        """.trimIndent()
        )
    }

    @Test
    fun testIfStatement_SingleLine() {
        myFixture.configureByText(
            LuaFileType, """
                if   true   then  elseif   false  then  else  end
            """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
                if true then elseif false then else end
        """.trimIndent()
        )
    }

    @Test
    fun testIfStatement_MultiLine() {
        myFixture.configureByText(
            LuaFileType, """
                if   true   then 
                
                elseif   false   then 
                
                else  
                
                end
            """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
                if true then
                elseif false then
                else
                end
        """.trimIndent()
        )
    }

    @Test
    fun testRepeatStatement_SingleLine() {
        myFixture.configureByText(
            LuaFileType, """
                repeat   until   true
            """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
                repeat until true
        """.trimIndent()
        )
    }

    @Test
    fun testRepeatStatement_MultiLine() {
        myFixture.configureByText(
            LuaFileType, """
                repeat 
                
                until true
            """.trimIndent()
        )

        reformatText {}

        myFixture.checkResult(
            """
                repeat
                until true
        """.trimIndent()
        )
    }

}