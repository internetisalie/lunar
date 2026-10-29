package net.internetisalie.lunar.lang

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.completion.LuaDeduplicationChecker
import net.internetisalie.lunar.lang.psi.LuaFile
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-550 — only the call applied directly to the bare name `require` is a require.
 *
 * `require("x").f("y")` parses as one `LuaFuncCall` whose head name is `require`, so a check on the
 * head name alone recognised `"y"` as a module name too, and missed `"x"` entirely when the chain
 * ended in an index (`local t = require("x").f`) and so had no `LuaFuncCall` of its own.
 */
@RunWith(JUnit4::class)
class LuaRequireChainedCallReferenceTest : BasePlatformTestCase() {
    private fun requiredNames(text: String): List<String> {
        myFixture.configureByText("consumer.lua", text)
        return PsiTreeUtil
            .collectElements(myFixture.file) { true }
            .flatMap { it.references.asIterable() }
            .filterIsInstance<LuaRequireReference>()
            .map { it.canonicalText.trim('"') }
    }

    private fun assertRequires(
        text: String,
        expected: List<String>,
    ) = assertEquals("Module names required by: $text", expected, requiredNames(text))

    @Test
    fun parenthesisedCallIsARequire() = assertRequires("""require("x")""", listOf("x"))

    @Test
    fun assignedCallIsARequire() = assertRequires("""local m = require("x")""", listOf("x"))

    @Test
    fun stringCallIsARequire() = assertRequires("""require "x"""", listOf("x"))

    @Test
    fun chainedFieldCallArgumentIsNotARequire() = assertRequires("""require("x").f("y")""", listOf("x"))

    @Test
    fun chainedStringCallArgumentIsNotARequire() = assertRequires("""require "x".f "y"""", listOf("x"))

    @Test
    fun chainedMethodCallArgumentIsNotARequire() = assertRequires("""require("x"):m("y")""", listOf("x"))

    @Test
    fun chainedTableArgumentIsNotARequire() = assertRequires("""require("x").f({ k = "y" })""", listOf("x"))

    @Test
    fun indexEndingChainIsARequire() = assertRequires("""local t = require("x").f""", listOf("x"))

    @Test
    fun chainNestedInAnotherCallIsARequire() = assertRequires("""print(require("x").f)""", listOf("x"))

    @Test
    fun calledResultArgumentIsNotARequire() = assertRequires("""require("x")("y")""", listOf("x"))

    @Test
    fun fieldNamedRequireIsNotARequire() = assertRequires("""t.require("x")""", emptyList())

    @Test
    fun methodCalledOnRequireIsNotARequire() = assertRequires("""require:m("x")""", emptyList())

    @Test
    fun callAfterAFieldOfRequireIsNotARequire() = assertRequires("""local t = require.a("x").b""", emptyList())

    @Test
    fun calledResultInAnIndexEndingChainIsNotARequire() =
        assertRequires("""local t = require("x")("y").f""", listOf("x"))

    @Test
    fun deduplicationSeesOnlyTheRequiredModule() {
        val file = myFixture.configureByText("consumer.lua", """require("x").f("y")""") as LuaFile
        assertTrue("x is required", LuaDeduplicationChecker.isAlreadyRequired(file, "x"))
        assertFalse("y is an ordinary string", LuaDeduplicationChecker.isAlreadyRequired(file, "y"))
    }
}
