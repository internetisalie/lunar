package net.internetisalie.lunar.lang

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-389 — `require "mod"` must contribute a reference, exactly as `require("mod")` does.
 *
 * The grammar admits `args ::= '(' [exprList] ')' | tableConstructor | STRING`, so the paren-less
 * form puts the module name in a bare STRING leaf rather than a `LuaTerminalExpr`. The contributor
 * matched only the latter, so Go to Definition did nothing on the form luacheck uses ~150 times.
 */
@RunWith(JUnit4::class)
class LuaRequireStringCallReferenceTest : BasePlatformTestCase() {

    private fun requireReferenceAt(text: String): LuaRequireReference? {
        myFixture.configureByText("consumer.lua", text)
        val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null
        return generateSequence<com.intellij.psi.PsiElement>(element) { it.parent }
            .take(4)
            .flatMap { it.references.asSequence() }
            .filterIsInstance<LuaRequireReference>()
            .firstOrNull()
    }

    @Test
    fun parenthesisedFormStillContributesAReference() {
        myFixture.addFileToProject("target.lua", "return {}\n")
        assertNotNull("Regression guard for the shape that already worked", requireReferenceAt("""local m = require("tar<caret>get")"""))
    }

    @Test
    fun stringCallFormContributesAReference() {
        myFixture.addFileToProject("target.lua", "return {}\n")
        assertNotNull(
            "require \"target\" must contribute a reference",
            requireReferenceAt("""local m = require "tar<caret>get""""),
        )
    }

    @Test
    fun longBracketStringCallFormContributesAReference() {
        myFixture.addFileToProject("target.lua", "return {}\n")
        assertNotNull(
            "require [[target]] is the same call shape",
            requireReferenceAt("local m = require [[tar<caret>get]]"),
        )
    }

    @Test
    fun stringCallFormResolvesToTheModuleFile() {
        val target = myFixture.addFileToProject("target.lua", "return {}\n")
        val resolved = requireReferenceAt("""local m = require "tar<caret>get"""")?.resolve()
        assertEquals("The paren-less form must resolve to the same file as require(...)", target, resolved)
    }

    @Test
    fun nonRequireStringCallContributesNothing() {
        // `print "x"` is the same grammar shape but not a require — it must stay inert.
        assertNull(requireReferenceAt("""print "tar<caret>get""""))
    }

    @Test
    fun exactlyOneReferenceIsContributedPerCall() {
        myFixture.addFileToProject("target.lua", "return {}\n")
        myFixture.configureByText("consumer.lua", """local m = require "target"""")
        val all = com.intellij.psi.util.PsiTreeUtil.collectElements(myFixture.file) { true }
            .flatMap { it.references.asIterable() }
            .filterIsInstance<LuaRequireReference>()
        assertEquals("The two contributor branches must not both fire", 1, all.size)
    }
}
