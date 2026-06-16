package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * SYNTAX-05: a separator line is drawn above a function that follows preceding code, but not
 * above the first declaration, and only when "Show method separators" is enabled.
 */
class LuaMethodSeparatorTest : BaseDocumentTest() {
    private val provider = LuaMethodSeparatorProvider()

    private fun withSeparators(enabled: Boolean, body: () -> Unit) {
        val settings = DaemonCodeAnalyzerSettings.getInstance()
        val previous = settings.SHOW_METHOD_SEPARATORS
        settings.SHOW_METHOD_SEPARATORS = enabled
        try {
            body()
        } finally {
            settings.SHOW_METHOD_SEPARATORS = previous
        }
    }

    private fun anchorOf(decl: PsiElement): PsiElement = PsiTreeUtil.getDeepestFirst(decl)

    @Test
    fun testSeparatorAboveSecondFunctionOnly() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText(
                    "test.lua",
                    """
                    local function a()
                    end
                    local function b()
                    end
                    """.trimIndent()
                )
                val decls = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaLocalFuncDecl::class.java).toList()
                assertEquals(2, decls.size)

                withSeparators(enabled = true) {
                    // First function: nothing above it → no separator.
                    assertNull(provider.getLineMarkerInfo(anchorOf(decls[0])))

                    // Second function: follows the first → separator on top.
                    val info = provider.getLineMarkerInfo(anchorOf(decls[1]))
                    assertNotNull(info, "expected a separator above the second function")
                    assertEquals(SeparatorPlacement.TOP, info.separatorPlacement)
                }
            }
        }
    }

    @Test
    fun testNoSeparatorWhenSettingDisabled() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText(
                    "test.lua",
                    """
                    local function a()
                    end
                    local function b()
                    end
                    """.trimIndent()
                )
                val decls = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaLocalFuncDecl::class.java).toList()

                withSeparators(enabled = false) {
                    assertNull(provider.getLineMarkerInfo(anchorOf(decls[1])))
                }
            }
        }
    }
}
