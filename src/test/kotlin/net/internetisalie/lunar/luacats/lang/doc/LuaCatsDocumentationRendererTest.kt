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

    @Test
    fun testMarkdownCodeBlock() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- This is a code block:
                    --- ```lua
                    --- local x = 10
                    --- ```
                    function <caret>code_test() end
                """.trimIndent())

                val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
                val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
                val doc = LuaCatsDocumentationRenderer.renderDoc(element!!)
                println("Markdown Code Block doc:\n$doc")

                assertNotNull(doc)
                assertContains(doc, "<div class='content'>")
                assertContains(doc, "local")
                assertContains(doc, "10")
                // Verify syntax highlighting is applied
                assertContains(doc, "<font color=")
            }
        }
    }
    @Test
    fun testStructuredTypeIsHtmlEscaped() {
        // TC-02a (#35): table<string, integer> must render as escaped code, not a raw <table
        // tag or an unescaped angle bracket, and must not produce a psi_element://table< href.
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val doc = renderLocalVarDoc(
                    """
                    ---@type table<string, integer>
                    local <caret>m = {}
                    """.trimIndent(),
                )
                assertContains(doc, "table&lt;string, integer&gt;")
                assertTrue(!doc.contains("<table"), "Doc contains an unescaped <table: $doc")
                assertTrue(!doc.contains("psi_element://table<"), "Doc has a broken psi_element href: $doc")
            }
        }
    }

    @Test
    fun testTypeLocalRendersAsLocalNotClass() {
        // TC-02b (#57): ---@type Player local renders `local m : ` + linked Player, not `class Player`.
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val doc = renderLocalVarDoc(
                    """
                    ---@type Player
                    local <caret>m = {}
                    """.trimIndent(),
                )
                assertContains(doc, "local")
                assertContains(doc, "Player")
                assertTrue(!doc.contains("class Player"), "Doc still renders 'class Player': $doc")
                assertTrue(doc.contains("psi_element://Player"), "Simple type Player not hyperlinked: $doc")
            }
        }
    }

    @Test
    fun testSimpleParamTypeIsHyperlinked() {
        // TC-02c (#35): a simple identifier param type is hyperlinked; HTML stays well-formed.
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    ---@param a Player
                    function <caret>f(a) end
                """.trimIndent())

                val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
                val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
                val doc = LuaCatsDocumentationRenderer.renderDoc(element!!)
                assertNotNull(doc)
                assertContains(doc!!, "psi_element://Player")
                assertTrue(!doc.contains("<Player"), "Doc contains an unescaped <Player: $doc")
            }
        }
    }

    private fun renderLocalVarDoc(code: String): String {
        configureByText(code)
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
        val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
        val doc = LuaCatsDocumentationRenderer.renderDoc(element!!)
        assertNotNull(doc)
        return doc!!
    }

    private fun assertContains(text: String, substring: String) {
        assertTrue(text.contains(substring), "Expected to find '$substring' in '$text'")
    }
}
