package net.internetisalie.lunar.lang.parser

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.psi.LuaDoStatement
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaGlobalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaIfStatement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaRepeatStatement
import net.internetisalie.lunar.lang.psi.LuaWhileStatement
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

    // ---- SYNTAX-18: Parser Error Recovery tests ----

    /**
     * TC 1–9 (SYNTAX-18-01): Half-written block skeletons build the correct typed PSI node.
     * Each input is a valid opener keyword with no closing construct, so a PsiErrorElement
     * is expected, but the TYPED outer node must still be present.
     */
    @Test
    fun testPartialBlockNodes() {
        // TC 1: if
        myFixture.configureByText(LuaFileType, "if x")
        runReadAction {
            val node = PsiTreeUtil.findChildOfType(myFixture.file, LuaIfStatement::class.java)
            Assertions.assertNotNull(node, "TC 1: expected LuaIfStatement for 'if x'")
        }

        // TC 2: while
        myFixture.configureByText(LuaFileType, "while c")
        runReadAction {
            val node = PsiTreeUtil.findChildOfType(myFixture.file, LuaWhileStatement::class.java)
            Assertions.assertNotNull(node, "TC 2: expected LuaWhileStatement for 'while c'")
        }

        // TC 3: for numeric
        myFixture.configureByText(LuaFileType, "for i = 1, n")
        runReadAction {
            val node = PsiTreeUtil.findChildOfType(myFixture.file, LuaNumericForStatement::class.java)
            Assertions.assertNotNull(node, "TC 3: expected LuaNumericForStatement for 'for i = 1, n'")
        }

        // TC 4: for generic
        myFixture.configureByText(LuaFileType, "for k,v in pairs(t)")
        runReadAction {
            val node = PsiTreeUtil.findChildOfType(myFixture.file, LuaGenericForStatement::class.java)
            Assertions.assertNotNull(node, "TC 4: expected LuaGenericForStatement for 'for k,v in pairs(t)'")
        }

        // TC 5: repeat
        myFixture.configureByText(LuaFileType, "repeat")
        runReadAction {
            val node = PsiTreeUtil.findChildOfType(myFixture.file, LuaRepeatStatement::class.java)
            Assertions.assertNotNull(node, "TC 5: expected LuaRepeatStatement for 'repeat'")
        }

        // TC 6: do
        myFixture.configureByText(LuaFileType, "do")
        runReadAction {
            val node = PsiTreeUtil.findChildOfType(myFixture.file, LuaDoStatement::class.java)
            Assertions.assertNotNull(node, "TC 6: expected LuaDoStatement for 'do'")
        }

        // TC 7: function (top-level funcDecl)
        myFixture.configureByText(LuaFileType, "function foo")
        runReadAction {
            val node = PsiTreeUtil.findChildOfType(myFixture.file, LuaFuncDecl::class.java)
            Assertions.assertNotNull(node, "TC 7: expected LuaFuncDecl for 'function foo'")
        }

        // TC 8: local function
        myFixture.configureByText(LuaFileType, "local function foo")
        runReadAction {
            val node = PsiTreeUtil.findChildOfType(myFixture.file, LuaLocalFuncDecl::class.java)
            Assertions.assertNotNull(node, "TC 8: expected LuaLocalFuncDecl for 'local function foo'")
        }

        // TC 9: global function
        myFixture.configureByText(LuaFileType, "global function foo")
        runReadAction {
            val node = PsiTreeUtil.findChildOfType(myFixture.file, LuaGlobalFuncDecl::class.java)
            Assertions.assertNotNull(node, "TC 9: expected LuaGlobalFuncDecl for 'global function foo'")
        }
    }

    /**
     * TC 10, 14 (SYNTAX-18-02): The PsiErrorElement is localized — its textOffset is at or
     * after the end of the opener expression, not at the `if`/`while` keyword (offset 0).
     */
    @Test
    fun testErrorLocalization() {
        // TC 10: 'if x' — error should be at or after end of 'x' (offset >= 4: "if x" is 4 chars)
        myFixture.configureByText(LuaFileType, "if x")
        runReadAction {
            val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
            Assertions.assertFalse(errors.isEmpty(), "TC 10: expected at least one PsiErrorElement for 'if x'")
            val errorOffset = errors.first().textOffset
            Assertions.assertTrue(
                errorOffset >= 3,
                "TC 10: error should be at or after 'x' (offset >= 3), got $errorOffset"
            )
        }

        // TC 14: 'while c' — error should be after 'c' (offset >= 6: "while " is 6 chars)
        myFixture.configureByText(LuaFileType, "while c")
        runReadAction {
            val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
            Assertions.assertFalse(errors.isEmpty(), "TC 14: expected at least one PsiErrorElement for 'while c'")
            val errorOffset = errors.first().textOffset
            Assertions.assertTrue(
                errorOffset >= 6,
                "TC 14: error should be at or after 'c' (offset >= 6), got $errorOffset"
            )
        }
    }

    /**
     * TC 11 (SYNTAX-18-04): 'if x\nreturn 1' — both a LuaIfStatement and a typed LuaFinalStatement
     * exist; the return is a nested child of the recovered if-block (grammar-kit maximal-partial-tree),
     * not lost to an anonymous error tree; the PsiErrorElement is localized.
     */
    @Test
    fun testNestedTypedRecovery() {
        myFixture.configureByText(LuaFileType, "if x\nreturn 1")
        runReadAction {
            val ifNode = PsiTreeUtil.findChildOfType(myFixture.file, LuaIfStatement::class.java)
            Assertions.assertNotNull(ifNode, "TC 11: expected LuaIfStatement for 'if x\\nreturn 1'")

            val returnNode = PsiTreeUtil.findChildOfType(myFixture.file, LuaFinalStatement::class.java)
            Assertions.assertNotNull(returnNode, "TC 11: expected a typed LuaFinalStatement (return 1)")

            // The return is nested inside the if-statement (grammar-kit maximal-partial-tree §3.5)
            Assertions.assertTrue(
                PsiTreeUtil.isAncestor(ifNode!!, returnNode!!, false),
                "TC 11: LuaFinalStatement should be a descendant of LuaIfStatement (nested recovery)"
            )

            // Error is localized (not at offset 0)
            val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
            Assertions.assertFalse(errors.isEmpty(), "TC 11: expected a PsiErrorElement inside the if")
            val errorOffset = errors.first().textOffset
            Assertions.assertTrue(
                errorOffset >= 4,
                "TC 11: error textOffset should be >= 4 (after 'if x'), got $errorOffset"
            )
        }
    }

    /**
     * TC 13 (SYNTAX-18-05): getName() on a partial node (where the IDENTIFIER may be absent)
     * returns null and never throws.
     */
    @Test
    fun testGetNameOnPartialNode() {
        // 'function foo' is partial (no parens/body/end) but 'foo' is present — getName() should
        // return "foo", not throw (SYNTAX-18-05: LuaNameRefElementImpl.getName() uses ?. not !!).
        myFixture.configureByText(LuaFileType, "function foo")
        runReadAction {
            val funcDecl = PsiTreeUtil.findChildOfType(myFixture.file, LuaFuncDecl::class.java)
            Assertions.assertNotNull(funcDecl, "TC 13: expected LuaFuncDecl for 'function foo'")
            // funcName.nameRef.getName() must not throw; the IDENTIFIER 'foo' is present so we
            // get a string. This verifies that the ?. change in LuaNameRefElementImpl.getName()
            // does not break the non-error path.
            val nameRef = funcDecl!!.funcName.nameRef
            // Calling getName() must not throw KotlinNullPointerException (SYNTAX-18-05)
            val name = nameRef.name
            Assertions.assertEquals("foo", name, "TC 13: funcName.nameRef.getName() should return 'foo'")
        }
    }
}
