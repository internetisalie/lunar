package net.internetisalie.lunar.lang

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.structure.LuaFunctionStructureViewTreeElement
import net.internetisalie.lunar.lang.structure.LuaLocalFunctionStructureViewTreeElement
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-36: while a function's `end` is still missing, the parser can leave the declaration with no
 * block child. The generated `getBlock()` is non-null and throws, so every caller that reaches a
 * declaration on the fly must tolerate the missing block.
 */
@RunWith(JUnit4::class)
class LuaBlocklessFunctionResolveTest : BasePlatformTestCase() {
    @Test
    fun testResolveAfterLocalFunctionWithoutBlock() {
        myFixture.configureByText("test.lua", "local function <caret>f(p)\n  p")

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, LuaLocalFuncDecl::class.java)!!
        assertNull(PsiTreeUtil.getChildOfType(declaration, LuaBlock::class.java))
        resolveAtCaret()
    }

    @Test
    fun testResolveAfterGlobalFunctionWithoutBlock() {
        myFixture.configureByText("test.lua", "function <caret>g(p)\n  p")

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, LuaFuncDecl::class.java)!!
        assertNull(PsiTreeUtil.getChildOfType(declaration, LuaBlock::class.java))
        resolveAtCaret()
    }

    @Test
    fun testStructureViewOfFunctionsWithoutBlock() {
        myFixture.configureByText("test.lua", "local function f(p)\n  p\nfunction g(p)\n  p")

        val local = PsiTreeUtil.findChildOfType(myFixture.file, LuaLocalFuncDecl::class.java)!!
        val global = PsiTreeUtil.findChildOfType(myFixture.file, LuaFuncDecl::class.java)!!
        LuaLocalFunctionStructureViewTreeElement(local).children
        LuaFunctionStructureViewTreeElement(global).children
    }

    private fun resolveAtCaret() {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
        element.reference!!.resolve()
    }
}
