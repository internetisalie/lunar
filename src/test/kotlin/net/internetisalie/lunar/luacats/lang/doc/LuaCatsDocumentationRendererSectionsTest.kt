package net.internetisalie.lunar.luacats.lang.doc

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.LuaCommentOwner
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for the under-exercised sections of [LuaCatsDocumentationRenderer.renderDoc]
 * (MAINT-16-04): See-Also (URL and plain-reference forms), Deprecated, named return,
 * generic type-param block, local-function signature, and the null contract for an
 * unsupported element. Mirrors the threading pattern of `LuaCatsDocumentationRendererTest`.
 */
class LuaCatsDocumentationRendererSectionsTest : BaseDocumentTest() {

    private fun renderAtCaret(text: String): String {
        configureByText(text)
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(elementAtCaret, "Could not find element at caret")
        val owner = PsiTreeUtil.getParentOfType(elementAtCaret, LuaCommentOwner::class.java, false)
        assertNotNull(owner, "Could not find LuaCommentOwner at caret")
        val doc = LuaCatsDocumentationRenderer.renderDoc(owner)
        assertNotNull(doc, "renderDoc returned null")
        return doc
    }

    private fun assertContains(text: String, substring: String) {
        assertTrue(text.contains(substring), "Expected to find '$substring' in '$text'")
    }

    @Test
    fun testSeeSectionUrl() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val doc = renderAtCaret(
                    """
                    ---@see https://lua.org The manual
                    function <caret>f() end
                    """.trimIndent(),
                )
                assertContains(doc, "See Also:")
                assertContains(doc, "<a href=\"https://lua.org\">")
            }
        }
    }

    @Test
    fun testSeeSectionPlainReference() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val doc = renderAtCaret(
                    """
                    ---@see other.func Related
                    function <caret>f() end
                    """.trimIndent(),
                )
                assertContains(doc, "<code>other.func</code>")
                assertTrue(!doc.contains("<a href"), "Plain reference must not emit an <a href> link")
            }
        }
    }

    @Test
    fun testDeprecatedSection() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val doc = renderAtCaret(
                    """
                    ---@deprecated Use bar instead
                    function <caret>foo() end
                    """.trimIndent(),
                )
                assertContains(doc, "⚠ Deprecated:")
                assertContains(doc, "Use bar instead")
            }
        }
    }

    @Test
    fun testNamedReturnRow() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val doc = renderAtCaret(
                    """
                    ---@return number count The total
                    function <caret>f() end
                    """.trimIndent(),
                )
                assertContains(doc, "Returns:")
                assertContains(doc, "number")
                assertContains(doc, "<code>count</code>")
            }
        }
    }

    @Test
    fun testGenericTypeParamBlock() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val doc = renderAtCaret(
                    """
                    ---@generic T
                    ---@param x T
                    ---@return T
                    function <caret>id(x) return x end
                    """.trimIndent(),
                )
                // buildFunctionSignatureTypeParams emits '<' / '>' operators around the param name.
                assertContains(doc, "&lt;")
                assertContains(doc, "T")
            }
        }
    }

    @Test
    fun testLocalFunctionSignature() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val doc = renderAtCaret(
                    """
                    ---@param n number
                    local function <caret>f(n) end
                    """.trimIndent(),
                )
                assertContains(doc, "local function")
                assertContains(doc, "f")
            }
        }
    }

    @Test
    fun testGrandparentFieldsRender() {
        // TC-03a (#67): C : B : A; A has field id -> Inherited Fields for C lists the grandparent field.
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val doc = renderAtCaret(
                    """
                    ---@class A
                    ---@field id integer
                    local A = {}

                    ---@class B : A
                    local B = {}

                    ---@class C : B
                    local <caret>C = {}
                    """.trimIndent(),
                )
                assertContains(doc, "Inherited Fields:")
                assertContains(doc, "id")
            }
        }
    }

    @Test
    fun testBareParentClassFieldsFoundViaIndex() {
        // TC-03b (#36): a bare `--- @class Parent` (no host decl) is resolved via LuaCatsTypeNameIndex.
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val doc = renderAtCaret(
                    """
                    --- @class Parent
                    --- @field p string

                    ---@class Child : Parent
                    local <caret>Child = {}
                    """.trimIndent(),
                )
                assertContains(doc, "Inherited Fields:")
                assertContains(doc, "p")
            }
        }
    }

    @Test
    fun testCyclicInheritanceTerminates() {
        // TC-03c (#67): @class A : B; @class B : A must terminate (no stack overflow / hang).
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val doc = renderAtCaret(
                    """
                    ---@class A : B
                    ---@field a integer
                    local A = {}

                    ---@class B : A
                    ---@field b integer
                    local <caret>B = {}
                    """.trimIndent(),
                )
                assertNotNull(doc, "Cyclic inheritance render must terminate and produce output")
            }
        }
    }

    @Test
    fun testUnsupportedElementReturnsNull() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("local x = 1")
                assertNull(LuaCatsDocumentationRenderer.renderDoc(myFixture.file))
            }
        }
    }
}
