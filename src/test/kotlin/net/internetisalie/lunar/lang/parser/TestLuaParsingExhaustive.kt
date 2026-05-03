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
    fun testValidMethodCalls() {
        val cases = listOf(
            "obj:method()",                 // method call without arguments
            "obj:method(1, 2, 3)",          // method call with arguments
            "obj:method(arg1, {a=1}, ...)", // method call with table and varargs
            "t:foo():bar():baz()",          // chained method calls
            "obj:method{a = 1}",            // method call with table constructor as argument
            "obj:method'string'",           // method call with string literal as argument
            "x = obj:method()",             // assignment from method call
            "local a = obj:method(1)",      // local variable from method call
            "if obj:test() then end",       // method call in condition
            "return obj:get()",             // method call in return statement
            "f(obj:method())",              // method call as function argument
            "t[obj:key()] = 1",             // method call in table index
            "a, b, c = obj:multi()",        // multi-value return from method call
            "for i in obj:iter() do end",   // method call in for-in loop
            "obj.sub:method()",             // method on sub-table (dot then colon)
            "obj['sub']:method()",          // method on indexed sub-table (bracket then colon)
            "obj:a():b():c()"               // triple-nested method calls
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
    fun testOperatorPrecedence() {
        val cases = listOf(
            // Arithmetic precedence: *, /, % before +, -
            "return 1 + 2 * 3",
            "return 1 * 2 + 3 * 4",
            "return 10 - 5 - 2",  // left-associative
            "return 2 ^ 3 ^ 2",   // right-associative
            // Unary operators
            "return -1 + 2",
            "return not a and b",
            "return #t + 1",
            "return ~(a | b)",
            // Relational and logical operators
            "return a < b and c > d",
            "return a or b and c or d",
            // Concatenation
            "return a .. b .. c",
            // Bitwise operators with arithmetic
            "return (a & b) + (c | d)",
            "return a << 1 + 2",  // should parse as a << (1 + 2)
            "return 1 + 2 << 3",  // should parse as (1 + 2) << 3
            // Complex nested expressions
            "return (a + b) * (c - d) ^ 2",
            "return a and b or c and d",
            "return a < b or c > d and e == f",
            // String concatenation with other operators
            "return a .. b + c",
            "return (a + b) .. (c + d)",
            // Method calls in expressions with operators
            "return obj:get() + 1",
            "return a + obj:get() * 2"
        )
        cases.forEach { doTest(it) }
    }

    @Test
    fun testVarargsCoverage() {
        val cases = listOf(
            // Varargs in function definition
            "function f(...) end",
            "function f(a, b, ...) end",
            "function f(a, ...) local x, y, z = ... end",
            // Varargs usage
            "return ...",
            "return ..., 1, 2",
            "print(...)",
            "f(...)",
            "table.insert(t, ...)",
            // Varargs in table constructors
            "return {...}",
            "return {1, 2, ...}",
            "return {a = 1, ...}",  // Should this be valid? Lua 5.2+ allows it
            // Varargs in assignments
            "local a, b, c = ...",
            "a, b = ...",
            "x, y, z = ..., 1",
            // Multiple function calls returning varargs
            "return f(), g(), ...",
            "local a, b = f(), ...",
            // Varargs in expressions (select)
            "return select('#', ...)",
            "return select(1, ...)",
            // Method calls with varargs
            "obj:method(...)"
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
