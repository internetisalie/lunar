package net.internetisalie.lunar.lang.format

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.TestInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class TestLuaFormatBlock {
    lateinit var myFixture: CodeInsightTestFixture
    lateinit var myProjectDescriptor: LightProjectDescriptor

    @BeforeTest
    fun before(testInfo: TestInfo) {
        myProjectDescriptor = LightProjectDescriptor()

        val factory = IdeaTestFixtureFactory.getFixtureFactory()

        val lightFixtureBuilder = factory.createLightFixtureBuilder(
            myProjectDescriptor, testInfo.displayName
        )

        myFixture = factory.createCodeInsightFixture(
            lightFixtureBuilder.getFixture(),
            LightTempDirTestFixtureImpl(true),
        )
        myFixture.setUp()
    }

    @AfterTest
    fun after() {
        myFixture.tearDown()
    }

    fun reformatText(fn : (settings: CommonCodeStyleSettings) -> Unit) {
        fn(CodeStyle.getLanguageSettings(myFixture.file))
        WriteCommandAction.writeCommandAction(myFixture.project).run<RuntimeException?> {
            CodeStyleManager.getInstance(myFixture.project).reformatText(
                myFixture.file, listOf(myFixture.file.textRange)
            )
        }
    }

    @Test
    fun testDoBlock() {
        myFixture.configureByText(LuaFileType, """
             do
            print ""
             end
        """.trimIndent())

        reformatText { settings ->
            settings.SPACE_AROUND_ASSIGNMENT_OPERATORS = true
            settings.KEEP_BLANK_LINES_IN_CODE = 1
        }

        myFixture.checkResult("""
            do
                print ""
            end
        """.trimIndent())
    }

    @Test
    fun testWhileBlock() {
        myFixture.configureByText(LuaFileType, """
             while true do
            print ""
             end
        """.trimIndent())

        reformatText {}

        myFixture.checkResult("""
            while true do
                print ""
            end
        """.trimIndent())
    }

    @Test
    fun testRepeatBlock() {
        myFixture.configureByText(LuaFileType, """
             repeat
            print ""
             until false
        """.trimIndent())

        reformatText {}

        myFixture.checkResult("""
            repeat
                print ""
            until false
        """.trimIndent())
    }

    @Test
    fun testIfBlock() {
        myFixture.configureByText(LuaFileType, """
             if false then
            print "1"
             elseif true then
            print "2"
             else
            print "3"
             end
        """.trimIndent())

        reformatText {}

        myFixture.checkResult("""
            if false then
                print "1"
            elseif true then
                print "2"
            else
                print "3"
            end
        """.trimIndent())
    }

    @Test
    fun testNumericForBlock() {
        myFixture.configureByText(LuaFileType, """
             for a = 1, 2, 3 do
            print ""
             end
        """.trimIndent())

        reformatText { settings ->
            settings.SPACE_AROUND_ASSIGNMENT_OPERATORS = true
            settings.KEEP_BLANK_LINES_IN_CODE = 1
        }

        myFixture.checkResult("""
            for a = 1, 2, 3 do
                print ""
            end
        """.trimIndent())
    }

    @Test
    fun testGenericForBlock() {
        myFixture.configureByText(LuaFileType, """
             for a in 1, 2, 3 do
            print ""
             end
        """.trimIndent())

        reformatText { settings ->
            settings.SPACE_AROUND_ASSIGNMENT_OPERATORS = true
            settings.KEEP_BLANK_LINES_IN_CODE = 1
        }

        myFixture.checkResult("""
            for a in 1, 2, 3 do
                print ""
            end
        """.trimIndent())
    }

    @Test
    fun testFunctionBlock() {
        myFixture.configureByText(LuaFileType, """
            function test(a, 
            b, 
            c)
            print ""
            end
        """.trimIndent())

        reformatText {}

        myFixture.checkResult("""
            function test(a,
                          b,
                          c)
                print ""
            end
        """.trimIndent())
    }

    @Test
    fun testLabel() {
        myFixture.configureByText(LuaFileType, """
            if restart then 
              ::start::
              goto start   
            end
        """.trimIndent())

        reformatText {}

        myFixture.checkResult("""
            if restart then
            ::start::
                goto start
            end
        """.trimIndent())
    }
}
