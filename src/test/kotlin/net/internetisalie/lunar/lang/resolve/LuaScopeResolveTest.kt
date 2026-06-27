package net.internetisalie.lunar.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaScopeResolveTest : BasePlatformTestCase() {

    private fun resolveAtCaret(text: String): PsiElement? {
        myFixture.configureByText("test.lua", text.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null
        val reference = leaf.parent?.reference ?: return null
        return reference.resolve()
    }

    @Test
    fun testForwardReferenceDoesNotResolveToLaterLocal() {
        val resolved = resolveAtCaret("""
            print(<caret>x)
            local x = 1
        """)
        assertNull("Forward reference should not resolve to later local", resolved)
    }

    @Test
    fun testNestedBlockResolvesOuterLocal() {
        val resolved = resolveAtCaret("""
            local x = 1
            do
                print(<caret>x)
            end
        """)
        assertNotNull("Nested block should resolve outer local", resolved)
        assertEquals("x", resolved?.text)
    }

    @Test
    fun testReferenceAfterDeclarationResolves() {
        val resolved = resolveAtCaret("""
            local x = 1
            print(<caret>x)
        """)
        assertNotNull("Reference after declaration should resolve", resolved)
        assertEquals("x", resolved?.text)
    }
}
