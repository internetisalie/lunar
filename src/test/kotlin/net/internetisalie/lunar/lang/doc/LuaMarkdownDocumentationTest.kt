package net.internetisalie.lunar.lang.doc

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.LuaCommentOwner
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LuaMarkdownDocumentationTest : BaseDocumentTest() {

    @Test
    fun testPlainMarkdownRendering() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- This function performs a **complex** calculation.
                    ---
                    --- ### Usage
                    --- 1. Initialize the system.
                    --- 2. Call this function with a `config` table.
                    function <caret>do_magic(config) end
                """.trimIndent())

                val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
                val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
                assertNotNull(element, "Could not find LuaCommentOwner at caret")

                val doc = LuaDocumentationRenderer.renderFullDocumentation(element!!)
                assertNotNull(doc)

                // Verify basic Markdown rendering
                assertTrue(doc!!.contains("<strong>complex</strong>"), "Bold text should be rendered")
                assertTrue(doc.contains("<h3>Usage</h3>"), "Header should be rendered")
                assertTrue(doc.contains("<li>Initialize the system.</li>"), "List item should be rendered")
                assertTrue(doc.contains("<code>config</code>"), "Inline code should be rendered")
            }
        }
    }

    @Test
    fun testPlainMarkdownCodeBlock() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- Example usage:
                    --- ```lua
                    --- local x = 10
                    --- print(x)
                    --- ```
                    function <caret>code_test() end
                """.trimIndent())

                val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
                val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
                val doc = LuaDocumentationRenderer.renderFullDocumentation(element!!)

                assertNotNull(doc)
                // Verify code block is present
                assertTrue(doc!!.contains("<pre><code>"), "Code block should be rendered")

                // Verify syntax highlighting (check for font tags or specific keywords)
                assertTrue(doc.contains("<font color="), "Syntax highlighting should be applied")
                assertTrue(doc.contains("local"), "Keyword 'local' should be preserved")
                assertTrue(doc.contains("print"), "Function 'print' should be preserved")
            }
        }
    }

    @Test
    fun testContiguousComments() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- Line 1
                    --- Line 2

                    --- This should be separate
                    function <caret>separate_test() end
                """.trimIndent())

                val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
                val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
                val doc = LuaDocumentationRenderer.renderFullDocumentation(element!!)

                assertNotNull(doc)
                assertTrue(doc!!.contains("This should be separate"), "Only the last contiguous block should be used")
                assertTrue(!doc.contains("Line 1"), "Previous block should not be included")
            }
        }
    }

    @Test
    fun testEscapeCharacters() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- This is not a \@param tag.
                    --- This is a \*literal star\*.
                    function <caret>escape_test() end
                """.trimIndent())

                val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
                val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
                val doc = LuaDocumentationRenderer.renderFullDocumentation(element!!)

                assertNotNull(doc)
                // Markdown parser should handle the escaped star
                assertTrue(doc!!.contains("*literal star*"), "Escaped star should be literal")
                assertTrue(doc.contains("@param"), "Escaped @ should be literal")
            }
        }
    }

    @Test
    fun testMarkdownLinks() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- This function is described at [Lua.org](https://lua.org).
                    --- Also visit https://lua-users.org for more info.
                    function <caret>link_test() end
                """.trimIndent())

                val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
                val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
                val doc = LuaDocumentationRenderer.renderFullDocumentation(element!!)

                assertNotNull(doc)
                // Standard Markdown link
                assertTrue(doc!!.contains("<a href=\"https://lua.org\">Lua.org</a>"), "Markdown link should be rendered")

                // Plain URL (might fail if not using GFM or autolink)
                assertTrue(doc.contains("<a href=\"https://lua-users.org\">https://lua-users.org</a>"), "Plain URL should be autolinked")
            }
        }
    }

    @Test
    fun testSeeUrlLink() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- @see https://www.lua.org/manual/5.4/
                    function <caret>see_test() end
                """.trimIndent())

                val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
                val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
                val doc = LuaDocumentationRenderer.renderFullDocumentation(element!!)

                assertNotNull(doc)
                assertTrue(doc!!.contains("See Also:"), "Should have See Also section")
                assertTrue(doc.contains("<a href=\"https://www.lua.org/manual/5.4/\">"), "URL in @see should be a link")
            }
        }
    }

    @Test
    fun testParamDescriptionAutolink() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- @param p string More info at https://lua.org
                    function <caret>param_test(p) end
                """.trimIndent())

                val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
                val element = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
                val doc = LuaDocumentationRenderer.renderFullDocumentation(element!!)

                assertNotNull(doc)
                assertTrue(doc!!.contains("<a href=\"https://lua.org\">https://lua.org</a>"), "URL in @param description should be autolinked")
            }
        }
    }
}
