package net.internetisalie.lunar.luacats.lang.doc

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.doc.LuaDocumentationTargetProvider
import net.internetisalie.lunar.lang.psi.LuaCommentOwner
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LuaCatsDocumentationRendererTest : BaseDocumentTest() {

    @Test
    fun testFunctionDocumentation() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- Adds two numbers.
                    --- @param a number The first number.
                    --- @param b number The second number.
                    --- @return number The sum.
                    function <caret>add(a, b)
                        return a + b
                    end
                """.trimIndent())

                val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
                assertNotNull(elementAtCaret, "Could not find element at caret")
                val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
                assertNotNull(element, "Could not find LuaCommentOwner at caret. Element: $elementAtCaret")

                val doc = LuaCatsDocumentationRenderer.renderDoc(element!!)
                assertNotNull(doc)
                // Verify structure contains three blocks
                assertContains(doc!!, "<div class='definition'>")
                assertContains(doc, "<div class='content'>")
                assertContains(doc, "<table class='sections'>")

                // Verify content
                assertContains(doc, "add")
                assertContains(doc, "a")
                assertContains(doc, "number")
                assertContains(doc, "Adds two numbers.")
            }
        }
    }

    @Test
    fun testClassDocumentation() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- A player in the game.
                    --- @class Player
                    --- @field name string The player's name
                    local <caret>Player = {}
                """.trimIndent())

                val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
                assertNotNull(elementAtCaret, "Could not find element at caret")
                val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
                assertNotNull(element, "Could not find LuaCommentOwner at caret. Element: $elementAtCaret")

                val doc = LuaCatsDocumentationRenderer.renderDoc(element!!)

                assertNotNull(doc)
                assertContains(doc!!, "<div class='definition'>")
                assertContains(doc, "<div class='content'>")
                assertContains(doc, "class")
                assertContains(doc, "Player")
                assertContains(doc, "A player in the game.")
                assertContains(doc, "Fields:")
                assertContains(doc, "name")
                assertContains(doc, "string")
            }
        }
    }

    @Test
    fun testEnumDocumentation() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- Log levels.
                    --- @enum LogLevel
                    --- | "DEBUG" # Detailed debug info
                    --- | "INFO" # General info
                    local <caret>LogLevel = {}
                """.trimIndent())

                val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
                assertNotNull(elementAtCaret, "Could not find element at caret")
                val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
                assertNotNull(element, "Could not find LuaCommentOwner at caret")

                val doc = LuaCatsDocumentationRenderer.renderDoc(element!!)

                assertNotNull(doc)
                assertContains(doc!!, "<div class='definition'>")
                assertContains(doc, "<div class='content'>")
                assertContains(doc, "enum")
                assertContains(doc, "LogLevel")
                assertContains(doc, "Log levels.")
                assertContains(doc, "Values:")
                assertContains(doc, "DEBUG")
                assertContains(doc, "Detailed debug info")
                assertContains(doc, "INFO")
                assertContains(doc, "General info")
            }
        }
    }

    @Test
    fun testDocumentationResolution() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val provider = LuaDocumentationTargetProvider()

                // Test Function
                configureByText("--- doc\nfunction <caret>foo() end")
                val targets1 = provider.documentationTargets(myFixture.file, myFixture.caretOffset)
                assertTrue(targets1.isNotEmpty(), "Should find documentation target for function")

                // Test Class
                configureByText("--- @class Point\nlocal <caret>Point = {}")
                val targets2 = provider.documentationTargets(myFixture.file, myFixture.caretOffset)
                assertTrue(targets2.isNotEmpty(), "Should find documentation target for class")

                // Test Enum
                configureByText("--- @enum Color\nlocal <caret>Color = {}")
                val targets3 = provider.documentationTargets(myFixture.file, myFixture.caretOffset)
                assertTrue(targets3.isNotEmpty(), "Should find documentation target for enum")
            }
        }
    }

    private fun assertContains(text: String, substring: String) {
        assertTrue(text.contains(substring), "Expected to find '$substring' in '$text'")
    }
}
