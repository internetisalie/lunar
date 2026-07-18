package net.internetisalie.lunar.luacats.lang.psi.impl

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.LuaCommentOwner
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Coverage for the [LuaCatsLazyCommentImpl] `get*TagList()` accessors (MAINT-16-01).
 *
 * The comment is a lazy-parseable element; the accessors force parsing on first
 * `PsiTreeUtil` walk, so simply invoking them realises the tag PSI.
 */
class LuaCatsLazyCommentTest : BaseDocumentTest() {

    private fun catsComment(text: String): LuaCatsComment {
        configureByText(text)
        val owner = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaCommentOwner::class.java).first()
        val comment = owner.catsComment
        assertNotNull(comment, "Host decl should expose a LuaCATS comment")
        return comment
    }

    @Test
    fun testAliasTagList() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val comment = catsComment(
                    """
                    ---@alias Mode "r"|"w"
                    local m
                    """.trimIndent(),
                )
                assertEquals(1, comment.aliasTagList.size)
                assertEquals("Mode", comment.aliasTagList.first().argName.text)
            }
        }
    }

    @Test
    fun testSeeTagList() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val comment = catsComment(
                    """
                    ---@see http.get Fetches data
                    local x
                    """.trimIndent(),
                )
                assertEquals(1, comment.seeTagList.size)
                assertEquals("http.get", comment.seeTagList.first().argName.text)
            }
        }
    }

    @Test
    fun testOverloadTagList() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val comment = catsComment(
                    """
                    ---@overload fun(x: string): string
                    function f(x) end
                    """.trimIndent(),
                )
                assertEquals(1, comment.overloadTagList.size)
            }
        }
    }

    @Test
    fun testGenericTagList() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val comment = catsComment(
                    """
                    ---@generic T
                    ---@param x T
                    ---@return T
                    function id(x) end
                    """.trimIndent(),
                )
                assertEquals(1, comment.genericTagList.size)
                val params = comment.genericTagList.first().genericTypeParams?.genericTypeParamList
                assertNotNull(params, "Generic tag should expose type params")
                assertEquals("T", params.first().argName.text)
            }
        }
    }

    @Test
    fun testVersionTagList() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val comment = catsComment(
                    """
                    ---@version >5.2
                    local x
                    """.trimIndent(),
                )
                assertEquals(1, comment.versionTagList.size)
            }
        }
    }

    @Test
    fun testDescriptionListExcludesTagNestedDescriptions() {
        // TC-05a (#38): getDescriptionList() returns only top-level (direct-child) descriptions.
        // A @param's trailing description is nested inside the paramTag, so it must NOT leak in.
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val comment = catsComment(
                    """
                    ---@param x number Some desc
                    function f(x) end
                    """.trimIndent(),
                )
                assertEquals(
                    0,
                    comment.descriptionList.size,
                    "Top-level descriptionList must exclude the @param-nested description",
                )
                assertTrue(
                    comment.paramTagList.first().description?.text?.contains("Some desc") == true,
                    "The @param description should live on the param tag, not the comment",
                )
            }
        }
    }

    @Test
    fun testDescriptionListEmptyForBareParamComment() {
        // TC-05b (#38): a comment whose only content is a @param tag has an empty top-level
        // descriptionList — the isDocCommentEmpty-equivalent check is accurate (not skewed by
        // the tag subtree).
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val comment = catsComment(
                    """
                    ---@param x number
                    function f(x) end
                    """.trimIndent(),
                )
                assertEquals(0, comment.descriptionList.size)
            }
        }
    }

    @Test
    fun testParamTagList() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val comment = catsComment(
                    """
                    ---@param id number
                    ---@param name string
                    function f(id, name) end
                    """.trimIndent(),
                )
                assertEquals(2, comment.paramTagList.size)
                assertEquals("id", comment.paramTagList[0].argName?.text)
                assertEquals("number", comment.paramTagList[0].argType.text)
                assertTrue(comment.paramTagList[1].argType.text.contains("string"))
            }
        }
    }
}
