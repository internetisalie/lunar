package net.internetisalie.lunar.luacats.lang.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class LuaCatsParserTest : BaseDocumentTest() {

    private fun doTest(code: String, expectErrors: Boolean = false) {
        // configureByText starts a write action, so it shouldn't be in a read action.
        myFixture.configureByText(LuaFileType, code)

        com.intellij.openapi.application.runReadAction {
            // Find all LuaCats comment elements. They are lazily parsed.
            // We need to ensure they are parsed by visiting them.
            val file = myFixture.file

            // Use a visitor to trigger lazy parsing of all elements
            PsiTreeUtil.findChildrenOfAnyType(file, false, com.intellij.psi.PsiElement::class.java)

            println("PSI TREE FOR:\n$code")
            println(com.intellij.psi.impl.DebugUtil.psiToString(file, true))

            val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)

            if (expectErrors) {
                Assertions.assertFalse(errors.isEmpty(), "Expected parser errors but found none in:\n$code")
            } else {
                if (errors.isNotEmpty()) {
                    println("PARSER ERRORS IN:\n$code")
                    errors.forEach { println("  - ${it.errorDescription} at offset ${it.textOffset}") }
                }
                Assertions.assertTrue(errors.isEmpty(), "Found parser errors in:\n$code\nErrors: " + errors.joinToString { it.errorDescription })
            }
        }
    }
    @Test
    fun testBasicTags() {
        doTest("""
            ---@class Animal
            ---@field name string
            local a = {}
        """.trimIndent())
    }

    @Test
    fun testTypeUnions() {
        doTest("""
            ---@type string | number | boolean
            local x
        """.trimIndent())
    }

    @Test
    fun testFunctionSignatures() {
        doTest("""
            ---@type fun(a: string, b: number): boolean
            local f
        """.trimIndent())
    }

    @Test
    fun testGenerics() {
        doTest("""
            ---@generic T
            ---@param x T
            ---@return T
            function identity(x) return x end
        """.trimIndent())
    }

    @Test
    fun testOverloads() {
        doTest("""
            ---@overload fun(x: string): string
            ---@param x number
            ---@return number
            function f(x) return x end
        """.trimIndent())
    }

    @Test
    fun testLiteralTypes() {
        // Tests literal string and number unions per LuaCATS spec.
        doTest("""
            ---@type "auto" | "manual" | 1 | 2
            local mode
        """.trimIndent())
    }

    @Test
    fun testOperators() {
        // Tests operator overload syntax per LuaCATS spec.
        doTest("""
            ---@class Vector
            ---@operator add(Vector): Vector
            ---@operator unm: Vector
            local v = {}
        """.trimIndent())
    }

    @Test
    fun testCast() {
        doTest("""
            ---@cast x +string, -nil
            local x
        """.trimIndent())
    }

    @Test
    fun testAsync() {
        doTest("""
            ---@async
            function f() end
        """.trimIndent())
    }

    @Test
    fun testDiagnostic() {
        doTest("""
            ---@diagnostic disable: unused-local
            ---@diagnostic disable-next-line: undefined-global
            print(UNDEFINED)
        """.trimIndent())
    }

    @Test
    fun testDeprecated() {
        doTest("""
            ---@deprecated Use newFunc instead
            function oldFunc() end
        """.trimIndent())
    }

    @Test
    fun testMultiLineEnum() {
        // Tests multi-line enum syntax with ---|  continuation per LuaCATS spec.
        doTest("""
            ---@alias Direction
            ---| 'North' # Go north
            ---| 'South' # Go south
        """.trimIndent())
    }

    @Test
    fun testMultiLineEnumExtended() {
        // Tests extended multi-line enum with 5+ continuation lines
        // to ensure proper parsing of long enum sequences
        doTest("""
            ---@alias Color
            ---| 'Red'
            ---| 'Green'
            ---| 'Blue'
            ---| 'Yellow'
            ---| 'Orange'
        """.trimIndent())
    }

    @Test
    fun testNumericLiterals() {
        // Tests that numeric literals are parsed correctly
        doTest("""
            ---@type 1 | 2 | 3
            local x
        """.trimIndent())
    }

    @Test
    fun testComplexDescriptions() {
        // Tests descriptions with special characters, markdown, and code references
        doTest("""
            ---@param x string The input string (e.g., "hello")
            ---@deprecated Use newFunc instead - see docs
            function oldFunc(x) end
        """.trimIndent())
    }

    @Test
    fun testEdgeCasesNestedFunctions() {
        // Tests nested function type definitions
        doTest("""
            ---@type fun(f: fun(x: number): string): boolean
            local f
        """.trimIndent())
    }

    @Test
    fun testBrokenTag() {
        doTest("""
            ---@class
        """.trimIndent(), expectErrors = true)
    }

    @Test
    fun testNestedGenerics() {
        // NOTE: Nested generics are NOT part of the LuaCATS specification.
        // The official docs state "Generics are still WIP" with no nested examples.
        // See: https://luals.github.io/wiki/annotations/#generic
        // 
        // This test validates that the parser handles undefined syntax gracefully,
        // but the parsed structure may not be semantically correct.
        // 
        // Grammar: typeParam ::= NAME (spec-compliant - only simple names)
        // To support this, would need: typeParam ::= type (but spec doesn't require it)
        doTest("""
            ---@type Map<string, List<number>>
            local x
        """.trimIndent())
        // TODO: Add structural validation when/if nested generics are added to LuaCATS spec
    }
}
