package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.IndexedDocumentTest
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import javax.swing.Icon
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers NAV-05 method override / implement gutter markers (TC-NAV-05-01..03).
 *
 * Uses [IndexedDocumentTest] because the type engine resolves classes / `function Class:m` decls via
 * the stub index ([net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl.collectMethodMembers]).
 */
class LuaOverrideLineMarkerTest : IndexedDocumentTest() {

    /** TC-NAV-05-01: a concrete super method → OverridingMethod gutter targeting `Base:greet`. */
    @Test
    fun testOverrideMarkerNavigatesToSuper() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText(
                    "override.lua",
                    """
                    ---@class Base
                    local Base = {}
                    function Base:greet() end

                    ---@class Derived : Base
                    local Derived = {}
                    function Derived:greet() end
                    """.trimIndent(),
                )

                val gutter = overrideGutters().single { it.icon === AllIcons.Gutter.OverridingMethod }
                val targets = targetsOf(gutter)
                assertTrue(targets.isNotEmpty(), "Override marker should have a navigation target")
                val target = targets.single()
                assertTrue(target is LuaFuncDecl, "Target should be a function declaration")
                assertEquals(
                    "Base",
                    (target as LuaFuncDecl).funcName.nameRef.text,
                    "Override target should be Base:greet",
                )
                assertEquals("greet", methodNameOf(target))
            }
        }
    }

    /** TC-NAV-05-02: an abstract (`@field` function) super member → ImplementingMethod gutter. */
    @Test
    fun testImplementMarkerForFieldDeclaredMethod() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText(
                    "implement.lua",
                    """
                    ---@class Base
                    ---@field greet fun()
                    local Base = {}

                    ---@class Derived : Base
                    local Derived = {}
                    function Derived:greet() end
                    """.trimIndent(),
                )

                val implementing = overrideGutters().filter { it.icon === AllIcons.Gutter.ImplementingMethod }
                assertTrue(implementing.isNotEmpty(), "Implement marker not found for @field-declared method")
            }
        }
    }

    /** TC-NAV-05-03: a class with no superclass → no override/implement gutter. */
    @Test
    fun testNoMarkerWithoutSuperclass() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText(
                    "solo.lua",
                    """
                    ---@class Solo
                    local Solo = {}
                    function Solo:foo() end
                    """.trimIndent(),
                )

                assertTrue(overrideGutters().isEmpty(), "No override/implement gutter expected for a solo class")
            }
        }
    }

    private fun overrideGutters(): List<GutterMark> =
        myFixture.findAllGutters().filter {
            it.icon === AllIcons.Gutter.OverridingMethod || it.icon === AllIcons.Gutter.ImplementingMethod
        }

    private val GutterMark.icon: Icon?
        get() = (this as? LineMarkerInfo.LineMarkerGutterIconRenderer<*>)?.lineMarkerInfo?.icon

    private fun targetsOf(gutter: GutterMark): List<PsiElement> {
        val info = (gutter as LineMarkerInfo.LineMarkerGutterIconRenderer<*>).lineMarkerInfo
        val related = info as? RelatedItemLineMarkerInfo<*> ?: return emptyList()
        return related.createGotoRelatedItems().mapNotNull { it.element }
    }

    private fun methodNameOf(decl: LuaFuncDecl): String? =
        decl.funcName.funcNameMethod?.nameRef?.identifier?.text
            ?: decl.funcName.funcNamePropertyList.lastOrNull()?.nameRef?.identifier?.text
}
