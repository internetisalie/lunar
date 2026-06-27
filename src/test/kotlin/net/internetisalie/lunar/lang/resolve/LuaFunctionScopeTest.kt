package net.internetisalie.lunar.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaFunctionScopeTest : BasePlatformTestCase() {

    private fun resolveAtCaret(text: String): PsiElement? {
        myFixture.configureByText("test.lua", text.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null
        val reference = leaf.parent?.reference ?: return null
        return reference.resolve()
    }

    @Test
    fun testNumericForVariableResolvesInBody() {
        val resolved = resolveAtCaret("""
            for i = 1, 3 do
                print(<caret>i)
            end
        """)
        assertNotNull("Numeric for variable should resolve inside loop body", resolved)
        assertEquals("i", resolved?.text)
    }

    @Test
    fun testLoopVariableNotVisibleAfterLoop() {
        val resolved = resolveAtCaret("""
            for i = 1, 3 do
            end
            print(<caret>i)
        """)
        assertNull("Numeric for variable should not be visible after loop block", resolved)
    }

    @Test
    fun testFunctionParameterResolvesInBody() {
        val resolved = resolveAtCaret("""
            function greet(name)
                print(<caret>name)
            end
        """)
        assertNotNull("Function parameter should resolve inside function body", resolved)
        assertEquals("name", resolved?.text)
    }

    @Test
    fun testImplicitSelfResolvesInsideMethod() {
        val resolved = resolveAtCaret("""
            function obj:m()
                return <caret>self
            end
        """)
        assertNotNull("self should resolve inside a method block", resolved)
        assertEquals("m", resolved?.text)
        val funcDecl = com.intellij.psi.util.PsiTreeUtil.getParentOfType(resolved, LuaFuncDecl::class.java)
        assertNotNull("Resolved target should be part of a LuaFuncDecl", funcDecl)
        assertEquals("obj", funcDecl?.funcName?.nameRef?.identifier?.text)
    }
}
