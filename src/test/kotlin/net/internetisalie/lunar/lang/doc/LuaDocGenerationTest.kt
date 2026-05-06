package net.internetisalie.lunar.lang.doc

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class LuaDocGenerationTest : BaseDocumentTest() {

    @Test
    fun `test enter on three dashes above function generates boilerplate`() {
        val code = """
            ---<caret>
            function test(a, b)
                return a + b
            end
        """.trimIndent()

        myFixture.configureByText(LuaFileType, code)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val templateManager = TemplateManager.getInstance(myFixture.project)
            val activeTemplate = templateManager.getActiveTemplate(myFixture.editor)
            if (activeTemplate != null) {
                templateManager.finishTemplate(myFixture.editor)
            }
        }

        val expected = """
            --- description
            --- @param a any description
            --- @param b any description
            --- @return any description
            function test(a, b)
                return a + b
            end
        """.trimIndent()

        println("ACTUAL OUTPUT:\n[${myFixture.editor.document.text}]")
        myFixture.checkResult(expected)
    }

    @Test
    fun `test enter on three dashes above local function generates boilerplate`() {
        val code = """
            ---<caret>
            local function test(param1)
                return param1
            end
        """.trimIndent()

        myFixture.configureByText(LuaFileType, code)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val templateManager = TemplateManager.getInstance(myFixture.project)
            val activeTemplate = templateManager.getActiveTemplate(myFixture.editor)
            if (activeTemplate != null) {
                templateManager.finishTemplate(myFixture.editor)
            }
        }

        val expected = """
            --- description
            --- @param param1 any description
            --- @return any description
            local function test(param1)
                return param1
            end
        """.trimIndent()

        println("ACTUAL OUTPUT:\n[${myFixture.editor.document.text}]")
        myFixture.checkResult(expected)
    }

    @Test
    fun `test enter on three dashes above class table generates boilerplate`() {
        val code = """
            ---<caret>
            local MyClass = {}
        """.trimIndent()

        myFixture.configureByText(LuaFileType, code)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val templateManager = TemplateManager.getInstance(myFixture.project)
            val activeTemplate = templateManager.getActiveTemplate(myFixture.editor)
            if (activeTemplate != null) {
                templateManager.finishTemplate(myFixture.editor)
            }
        }

        val expected = """
            --- @class MyClass
            local MyClass = {}
        """.trimIndent()

        println("ACTUAL OUTPUT:\n[${myFixture.editor.document.text}]")
        myFixture.checkResult(expected)
    }

    @Test
    fun `test enter on three dashes above varargs function generates boilerplate`() {
        val code = """
            ---<caret>
            function process_items(a, ...)
                return a
            end
        """.trimIndent()

        myFixture.configureByText(LuaFileType, code)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val templateManager = TemplateManager.getInstance(myFixture.project)
            val activeTemplate = templateManager.getActiveTemplate(myFixture.editor)
            if (activeTemplate != null) {
                templateManager.finishTemplate(myFixture.editor)
            }
        }

        val expected = """
            --- description
            --- @param a any description
            --- @param ... any description
            --- @return any description
            function process_items(a, ...)
                return a
            end
        """.trimIndent()

        println("ACTUAL OUTPUT:\n[${myFixture.editor.document.text}]")
        myFixture.checkResult(expected)
    }

    @Test
    fun `test intention generates interactive boilerplate`() {
        val code = """
            <caret>function test(a, b)
                return a
            end
        """.trimIndent()

        myFixture.configureByText(LuaFileType, code)
        val intention = myFixture.findSingleIntention("Generate LuaCATS documentation")
        myFixture.launchAction(intention)

        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val templateManager = TemplateManager.getInstance(myFixture.project)
            val activeTemplate = templateManager.getActiveTemplate(myFixture.editor)
            if (activeTemplate != null) {
                templateManager.finishTemplate(myFixture.editor)
            }
        }

        val expected = """
            --- description
            --- @param a any description
            --- @param b any description
            --- @return any description
            function test(a, b)
                return a
            end
        """.trimIndent()

        println("ACTUAL OUTPUT:\n[${myFixture.editor.document.text}]")
        myFixture.checkResult(expected)
    }

    @Test
    fun `test type inference in boilerplate`() {
        val code = """
            ---<caret>
            function configure(name, count, is_enabled)
                return true
            end
        """.trimIndent()

        myFixture.configureByText(LuaFileType, code)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val templateManager = TemplateManager.getInstance(myFixture.project)
            val activeTemplate = templateManager.getActiveTemplate(myFixture.editor)
            if (activeTemplate != null) {
                templateManager.finishTemplate(myFixture.editor)
            }
        }

        val expected = """
            --- description
            --- @param name string description
            --- @param count number description
            --- @param is_enabled boolean description
            --- @return any description
            function configure(name, count, is_enabled)
                return true
            end
        """.trimIndent()

        myFixture.checkResult(expected)
    }
}
