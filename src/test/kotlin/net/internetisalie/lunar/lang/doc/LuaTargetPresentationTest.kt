package net.internetisalie.lunar.lang.doc

import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import java.awt.Color

/**
 * MAINT-44 TC-02: [LuaTargetPresentation] yields the presentable text, icon and background colour the
 * platform's internal `targetPresentation(PsiElement)` gives for Lunar PSI (design §2.2, §3.2).
 */
class LuaTargetPresentationTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        EditorTabColorProvider.EP_NAME.point.registerExtension(
            RedProjectViewColor(),
            LoadingOrder.FIRST,
            testRootDisposable,
        )
        myFixture.configureByText(
            "probe.lua",
            "---@class Animal\nlocal Animal = {}\n---Says hi\nfunction Animal:greet() end\n" +
                "local function helper() end\nAnimal.name = 'x'\n",
        )
    }

    fun testLocalVarDecl() = assertPresentation(firstOf(LuaLocalVarDecl::class.java), "local Animal = {}")

    fun testFuncDecl() = assertPresentation(firstOf(LuaFuncDecl::class.java), "function Animal:greet() end")

    fun testLocalFuncDecl() = assertPresentation(firstOf(LuaLocalFuncDecl::class.java), "local function helper() end")

    fun testNameRef() = assertPresentation(firstOf(LuaNameRef::class.java), "Animal")

    private fun <T : PsiElement> firstOf(type: Class<T>): T =
        PsiTreeUtil.findChildOfType(myFixture.file, type) ?: error("no ${type.simpleName} in fixture")

    private fun assertPresentation(
        element: PsiElement,
        expectedText: String,
    ) {
        val platform = targetPresentation(element)
        val presentation = LuaTargetPresentation.of(element)

        assertEquals(expectedText, presentation.presentableText)
        assertNotNull(presentation.icon)
        assertEquals(platform.icon, presentation.icon)
        assertEquals(Color.RED, presentation.backgroundColor)
        assertEquals(platform.backgroundColor, presentation.backgroundColor)
    }

    private class RedProjectViewColor :
        EditorTabColorProvider,
        DumbAware {
        override fun getEditorTabColor(
            project: Project,
            file: VirtualFile,
        ): Color? = null

        override fun getProjectViewColor(
            project: Project,
            file: VirtualFile,
        ): Color = Color.RED
    }
}
