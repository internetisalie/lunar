package net.internetisalie.lunar.lang.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Exhaustive parsing tests collected from official Lua tests, luacheck, and other sources.
 * This class uses a data-driven approach to verify that valid Lua code parses without errors
 * and invalid code produces [PsiErrorElement]s.
 */
class TestLuaParsingExhaustive : BaseDocumentTest() {

    private fun doTest(code: String, expectErrors: Boolean = false) {
        myFixture.configureByText(LuaFileType, code)
        val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        if (expectErrors) {
            Assertions.assertFalse(errors.isEmpty(), "Expected parser errors but found none in:\n$code")
        } else {
            Assertions.assertTrue(errors.isEmpty(), "Found parser errors in:\n$code\nErrors: " + errors.joinToString { it.errorDescription })
        }
    }

    @Test
    fun testValidAssignments() {
        val cases = listOf(
            "a = 1",
            "a, b = 1, 2",
            "a.x, b[1] = 1, 2",
            "a, b = f()",
            "(f()).x = 1",
            "a = 1; b = 2",
            "a = 1 b = 2",
            "t[f()] = g()",
            "local a, b = 1, 2",
            "local a; a = 1"
        )
        cases.forEach { doTest(it) }
    }

    @Test
    fun testValidControlFlow() {
        val cases = listOf(
            "if true then end",
            "if true then elseif false then else end",
            "while true do break end",
            "repeat until true",
            "for i=1,10 do end",
            "for i=1,10,2 do end",
            "for k,v in pairs(t) do end",
            "do end",
            "::label:: goto label",
            "if a then elseif b then elseif c then end"
        )
        cases.forEach { doTest(it) }
    }

    @Test
    fun testValidExpressions() {
        val cases = listOf(
            "return nil, true, false",
            "return 123, 0xff, 0.1e-2, 1.23e+10",
            "return 'str', \"str\", [[long str]]",
            "return ...",
            "return function() end",
            "return {1, 2, 3}",
            "return {a = 1, b = 2}",
            "return {[\"a\"] = 1, [1+1] = 2}",
            "return {1, 2; a = 3, b = 4,}",
            "return -1 + 2 * 3 ^ 4",
            "return not a or b and c",
            "return a .. b .. c",
            "return a ^ b ^ c",
            "return 1 << 2; return 3 >> 1",  // bitwise shift operators
            "return 5 // 2",
            "return 1 & 2",  // bitwise AND
            "return 1 | 2",  // bitwise OR
            "return ~1"      // bitwise NOT
        )
        cases.forEach { doTest(it) }
    }

    @Test
    fun testValidLexicalEdgeCases() {
        val cases = listOf(
            "s = \"\\n\\r\\t\\\"\\'\\\\\"",
            "s = \"\\xAF\\x00\"",
            "s = \"\\123\"",
            "s = \"\\u{1234}\"",
            "s = [[ multi-line \n bracket ]]",
            "s = [=[ nested [[]] ]=]",
            "s = [==[ [=[ ]=] ]==]",
            "s = --[[ long comment ]] 1"
        )
        cases.forEach { doTest(it) }
    }

    @Test
    fun testValidAmbiguities() {
        val cases = listOf(
            "a = b\n(c):d()", // One statement in Lua: a = b(c):d()
            "a = b; (c):d()", // Two statements
            "return\n1",      // return (nil) then statement 1 (if valid in context)
            "f()\n(g)()"      // f()(g)()
        )
        cases.forEach { doTest(it) }
    }

    @Test
    fun testInvalidSyntax() {
        val cases = listOf(
            "local a = {4",
            "function a(, ...) end",
            "while << do end",
            "if a then", // Missing end
            "for i=1 do end", // Missing comma/limit
            "[1] = 2", // Invalid statement start
            "local x = ;", // Invalid assignment
            "a:b"  // Incomplete method call (no arguments or function definition)
        )
        cases.forEach { doTest(it, expectErrors = true) }
    }

    @Test
    fun testLua54Attributes() {
        val cases = listOf(
            "local x <const> = 10",
            "local f <close> = io.open('t')",
            "local a <const>, b <close> = 1, 2"
        )
        cases.forEach { doTest(it) }
    }

    @Test
    fun testOfficialLuaErrorTests() {
        // Snippets from tests/errors.lua that are actual parser errors
        doTest("local a = {4", expectErrors = true)
        doTest("while << do end", expectErrors = true)
    }
}
