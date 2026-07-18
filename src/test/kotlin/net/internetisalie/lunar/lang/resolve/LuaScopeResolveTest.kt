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

    @Test
    fun testSelfReferentialInitializerRhsDoesNotResolveToNewLocal() {
        // TC-02 (MAINT-30-03, §3.3): the RHS `x` of `local x = x` is NOT in scope on its own
        // initializer (Lua §3.3.3/§3.5). It must resolve to an outer/undeclared `x`, never to the
        // enclosing `local x` being declared. Here there is no outer `x`, so it resolves to null.
        val resolved = resolveAtCaret("""
            local x = <caret>x
        """)
        assertNull("RHS of a self-referential initializer must not resolve to the new local", resolved)
    }

    @Test
    fun testSelfReferentialInitializerRhsResolvesToOuterLocal() {
        // TC-02 companion: with an OUTER `x`, the RHS `x` of the inner `local x = x` resolves to the
        // outer local (offset 6), proving the new local is excluded from its own RHS scope.
        val resolved = resolveAtCaret("""
            local x = 1
            local x = <caret>x
        """)
        assertNotNull("RHS of a self-referential initializer resolves to the outer local", resolved)
        assertEquals("x", resolved?.text)
        assertEquals("RHS must bind the OUTER local (offset 6), not the inner declaration", 6, resolved?.textOffset)
    }
}
