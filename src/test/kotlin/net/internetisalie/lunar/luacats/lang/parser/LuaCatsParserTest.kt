package net.internetisalie.lunar.luacats.lang.parser

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsAliasTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsArgType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsFieldTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsOverloadFunctionSignature
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsReturnTypeDescriptor
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsSeeTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeTag
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class LuaCatsParserTest : BaseDocumentTest() {
    private fun doTest(
        code: String,
        expectErrors: Boolean = false,
    ) {
        // configureByText starts a write action, so it shouldn't be in a read action.
        myFixture.configureByText(LuaFileType, code)

        com.intellij.openapi.application.runReadAction {
            // Find all LuaCats comment elements. They are lazily parsed.
            // We need to ensure they are parsed by visiting them.
            val file = myFixture.file

            // Use a visitor to trigger lazy parsing of all elements
            PsiTreeUtil.findChildrenOfAnyType(file, false, com.intellij.psi.PsiElement::class.java)

            println("PSI TREE FOR:\n$code")
            println(
                com.intellij.psi.impl.DebugUtil
                    .psiToString(file, true),
            )

            val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)

            if (expectErrors) {
                Assertions.assertFalse(errors.isEmpty(), "Expected parser errors but found none in:\n$code")
            } else {
                if (errors.isNotEmpty()) {
                    println("PARSER ERRORS IN:\n$code")
                    errors.forEach { println("  - ${it.errorDescription} at offset ${it.textOffset}") }
                }
                Assertions.assertTrue(
                    errors.isEmpty(),
                    "Found parser errors in:\n$code\nErrors: " + errors.joinToString { it.errorDescription },
                )
            }
        }
    }

    /**
     * Parses [code] error-free and asserts the type text of the first [tagClass] element.
     *
     * "No errors" alone is not evidence of a parse: a failed type under `@alias`/`@type`/`@return`
     * falls back to prose and `@param` to `ldocParamTag`, both silently (batch plan §2).
     */
    private fun assertTagType(
        code: String,
        tagClass: Class<out PsiElement>,
        expectedType: String,
    ) {
        doTest(code)
        runReadAction {
            val tag =
                requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, tagClass)) { "no ${tagClass.simpleName}" }
            val argType = PsiTreeUtil.getChildOfType(tag, LuaCatsArgType::class.java)
            Assertions.assertEquals(expectedType, argType?.text?.trim())
        }
    }

    private fun firstSeeTag(): LuaCatsSeeTag =
        requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, LuaCatsSeeTag::class.java)) { "no LuaCatsSeeTag" }

    @Test
    fun testBasicTags() {
        doTest(
            """
            ---@class Animal
            ---@field name string
            local a = {}
            """.trimIndent(),
        )
    }

    @Test
    fun testTypeUnions() {
        doTest(
            """
            ---@type string | number | boolean
            local x
            """.trimIndent(),
        )
    }

    @Test
    fun testFunctionSignatures() {
        doTest(
            """
            ---@type fun(a: string, b: number): boolean
            local f
            """.trimIndent(),
        )
    }

    @Test
    fun testGenerics() {
        doTest(
            """
            ---@generic T
            ---@param x T
            ---@return T
            function identity(x) return x end
            """.trimIndent(),
        )
    }

    @Test
    fun testOverloads() {
        doTest(
            """
            ---@overload fun(x: string): string
            ---@param x number
            ---@return number
            function f(x) return x end
            """.trimIndent(),
        )
    }

    @Test
    fun testLiteralTypes() {
        // Tests literal string and number unions per LuaCATS spec.
        doTest(
            """
            ---@type "auto" | "manual" | 1 | 2
            local mode
            """.trimIndent(),
        )
    }

    @Test
    fun testBooleanLiteralTypes() {
        // Tests boolean literal types (true/false); nil remains the nil type.
        doTest(
            """
            ---@type true | false | nil
            local flag
            """.trimIndent(),
        )
    }

    @Test
    fun testOperators() {
        // Tests operator overload syntax per LuaCATS spec.
        doTest(
            """
            ---@class Vector
            ---@operator add(Vector): Vector
            ---@operator unm: Vector
            local v = {}
            """.trimIndent(),
        )
    }

    @Test
    fun testCast() {
        doTest(
            """
            ---@cast x +string, -nil
            local x
            """.trimIndent(),
        )
    }

    @Test
    fun testAsync() {
        doTest(
            """
            ---@async
            function f() end
            """.trimIndent(),
        )
    }

    @Test
    fun testDiagnostic() {
        doTest(
            """
            ---@diagnostic disable: unused-local
            ---@diagnostic disable-next-line: undefined-global
            print(UNDEFINED)
            """.trimIndent(),
        )
    }

    @Test
    fun testDeprecated() {
        doTest(
            """
            ---@deprecated Use newFunc instead
            function oldFunc() end
            """.trimIndent(),
        )
    }

    @Test
    fun testMultiLineEnum() {
        // Tests multi-line enum syntax with ---|  continuation per LuaCATS spec.
        doTest(
            """
            ---@alias Direction
            ---| 'North' # Go north
            ---| 'South' # Go south
            """.trimIndent(),
        )
    }

    @Test
    fun testMultiLineEnumExtended() {
        // Tests extended multi-line enum with 5+ continuation lines
        // to ensure proper parsing of long enum sequences
        doTest(
            """
            ---@alias Color
            ---| 'Red'
            ---| 'Green'
            ---| 'Blue'
            ---| 'Yellow'
            ---| 'Orange'
            """.trimIndent(),
        )
    }

    @Test
    fun testNumericLiterals() {
        // Tests that numeric literals are parsed correctly
        doTest(
            """
            ---@type 1 | 2 | 3
            local x
            """.trimIndent(),
        )
    }

    @Test
    fun testComplexDescriptions() {
        // Tests descriptions with special characters, markdown, and code references
        doTest(
            """
            ---@param x string The input string (e.g., "hello")
            ---@deprecated Use newFunc instead - see docs
            function oldFunc(x) end
            """.trimIndent(),
        )
    }

    @Test
    fun testEdgeCasesNestedFunctions() {
        // Tests nested function type definitions
        doTest(
            """
            ---@type fun(f: fun(x: number): string): boolean
            local f
            """.trimIndent(),
        )
    }

    @Test
    fun testBrokenTag() {
        doTest(
            """
            ---@class
            """.trimIndent(),
            expectErrors = true,
        )
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
        doTest(
            """
            ---@type Map<string, List<number>>
            local x
            """.trimIndent(),
        )
        // TODO: Add structural validation when/if nested generics are added to LuaCATS spec
    }

    @Test
    fun testReturnTagDescriptions() {
        // BUG-355: EmmyLua @-description after @return type is a hard parse error
        doTest(
            """
            ---@return number|nil @The earliest expiry time.
            ---@return number|nil The earliest expiry time.
            ---@return number|nil # The earliest expiry time.
            function getNextTimerExpires() end
            """.trimIndent(),
        )
    }

    @Test
    fun testUnclosedBacktickDoesNotCorruptNextTagLine() {
        // MAINT-27 #19: an unclosed backtick must not consume across the newline into
        // the following tag line. The stray backtick on line 1 is a local error, but the
        // @param on line 2 must still parse normally (its NAME tokens intact, not swallowed).
        myFixture.configureByText(
            LuaFileType,
            """
            ---@param x `unclosed
            ---@param y number
            function f(x, y) end
            """.trimIndent(),
        )

        com.intellij.openapi.application.runReadAction {
            val file = myFixture.file
            PsiTreeUtil.findChildrenOfAnyType(file, false, com.intellij.psi.PsiElement::class.java)

            val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
            val maxErrorOffset = errors.maxOfOrNull { it.textOffset } ?: -1
            val line2Offset = myFixture.file.text.indexOf("@param y")

            Assertions.assertTrue(
                maxErrorOffset < line2Offset,
                "Backtick error leaked past line 1 into line 2 (@param y): errors at " +
                    errors.joinToString { it.textOffset.toString() },
            )

            val names =
                PsiTreeUtil
                    .findChildrenOfType(file, com.intellij.psi.PsiElement::class.java)
                    .filter { it.text == "y" || it.text == "number" }
            Assertions.assertTrue(
                names.any { it.text == "y" } && names.any { it.text == "number" },
                "Line 2 @param tokens were swallowed by the unclosed backtick",
            )
        }
    }

    @Test
    fun testUnicodeClassNameParses() {
        // MAINT-27 #66: a non-ASCII class name lexes as a single NAME (no BAD_CHARACTER).
        doTest(
            """
            ---@class 名前
            local x = {}
            """.trimIndent(),
        )
    }

    @Test
    fun testReturnMultipleCommaSeparated() {
        doTest(
            """
            ---@return string id, boolean status
            ---@return string id # The user id, boolean status
            ---@return number, string
            function myFunc() end
            """.trimIndent(),
        )
    }

    /** BUG-549: a Vim help-tag reference is one NAME, so `seeTag` takes it as the reference. */
    @Test
    fun testSeeVimHelpTagReference() {
        doTest(
            """
            ---@see |luaL_loadfile()|
            local function f() end
            """.trimIndent(),
        )
        runReadAction { Assertions.assertEquals("|luaL_loadfile()|", firstSeeTag().argName.text) }
    }

    @Test
    fun testSeeHelpTagWithDescription() {
        doTest(
            """
            ---@see |vim.fn| for details
            local function f() end
            """.trimIndent(),
        )
        runReadAction {
            val tag = firstSeeTag()
            Assertions.assertEquals("|vim.fn|", tag.argName.text)
            Assertions.assertEquals("for details", tag.description?.text?.trim())
        }
    }

    @Test
    fun testSeeUnterminatedPipeIsStillAnError() {
        doTest(
            """
            ---@see |luaL_loadfile()
            local function f() end
            """.trimIndent(),
            expectErrors = true,
        )
    }

    /** BUG-548: TAG_FIELD had no string rule, so a string-literal field type was a parse error. */
    @Test
    fun testStringLiteralUnionFieldType() {
        assertTagType(
            """
            ---@class P4
            ---@field source "lazy" | "packspec"
            local P4 = {}
            """.trimIndent(),
            LuaCatsFieldTag::class.java,
            "\"lazy\" | \"packspec\"",
        )
    }

    @Test
    fun testSingleStringLiteralFieldType() {
        assertTagType(
            """
            ---@class P4
            ---@field source "lazy"
            local P4 = {}
            """.trimIndent(),
            LuaCatsFieldTag::class.java,
            "\"lazy\"",
        )
    }

    @Test
    fun testUnterminatedStringFieldTypeIsStillAnError() {
        doTest(
            """
            ---@class P4
            ---@field source "lazy
            local P4 = {}
            """.trimIndent(),
            expectErrors = true,
        )
    }

    /** BUG-542: `tableLiteralEntry` had no slot for an optional-key marker. */
    @Test
    fun testOptionalKeyInFieldTableType() {
        assertTagType(
            """
            ---@class P1
            ---@field notifs {msg:string, file?:string}[]
            local P1 = {}
            """.trimIndent(),
            LuaCatsFieldTag::class.java,
            "{msg:string, file?:string}[]",
        )
    }

    /** On HEAD this one shows no error: the failed type falls to `description`, leaving no type. */
    @Test
    fun testOptionalKeyInAliasTableType() {
        assertTagType(
            """
            ---@alias Q5 {name:string, file?:string}
            local x = 1
            """.trimIndent(),
            LuaCatsAliasTag::class.java,
            "{name:string, file?:string}",
        )
    }

    @Test
    fun testOptionalOverloadParameter() {
        doTest(
            """
            ---@overload fun(opts?:table):table
            local function f(o) end
            """.trimIndent(),
        )
        runReadAction {
            val signature =
                requireNotNull(
                    PsiTreeUtil.findChildOfType(myFixture.file, LuaCatsOverloadFunctionSignature::class.java),
                )
            Assertions.assertEquals(1, signature.functionSignatureArgumentList.size)
        }
    }

    @Test
    fun testTableKeyWithoutColonIsStillAnError() {
        doTest(
            """
            ---@class P1
            ---@field p {file?string}
            local P1 = {}
            """.trimIndent(),
            expectErrors = true,
        )
    }

    /** BUG-543: TAG_FIELD had no NUMBER rule, so `[1]` lexed as prose. */
    @Test
    fun testNumericFieldKey() {
        assertTagType(
            """
            ---@class K
            ---@field [1] string lhs
            local K = {}
            """.trimIndent(),
            LuaCatsFieldTag::class.java,
            "string",
        )
    }

    /** BUG-543: a bracket key was accepted only by the single-entry `dictionaryType`. */
    @Test
    fun testNumericKeysInFieldTableType() {
        assertTagType(
            """
            ---@class K
            ---@field p {[1]:string, [string]:any}
            local K = {}
            """.trimIndent(),
            LuaCatsFieldTag::class.java,
            "{[1]:string, [string]:any}",
        )
    }

    @Test
    fun testNumericKeysInAliasTableType() {
        assertTagType(
            """
            ---@alias LazyProps {[1]:string, [2]:string|fun(), [3]?:string}[]
            local x = 1
            """.trimIndent(),
            LuaCatsAliasTag::class.java,
            "{[1]:string, [2]:string|fun(), [3]?:string}[]",
        )
    }

    @Test
    fun testBracketKeyWithoutColonIsStillAnError() {
        doTest(
            """
            ---@class K
            ---@field p {[1]string}
            local K = {}
            """.trimIndent(),
            expectErrors = true,
        )
    }

    /** BUG-544: a nested type took no `?` suffix, so a table member could not be optional. */
    @Test
    fun testOptionalFunReturnInTableType() {
        assertTagType(
            """
            ---@alias S1 {title:string, filter:fun(p:table):boolean?}
            local x = 1
            """.trimIndent(),
            LuaCatsAliasTag::class.java,
            "{title:string, filter:fun(p:table):boolean?}",
        )
    }

    /** On HEAD the `?` went to the tag's prose, leaving the type `fun(p:table):boolean`. */
    @Test
    fun testOptionalFunReturnAtTopLevelBelongsToTheType() {
        assertTagType(
            """
            ---@alias S2 fun(p:table):boolean?
            local x = 1
            """.trimIndent(),
            LuaCatsAliasTag::class.java,
            "fun(p:table):boolean?",
        )
    }

    @Test
    fun testOptionalValueInTableType() {
        assertTagType(
            """
            ---@alias Q1 {a:string?}
            local x = 1
            """.trimIndent(),
            LuaCatsAliasTag::class.java,
            "{a:string?}",
        )
    }

    /** A tag's own type keeps `?` out, or `string?` would be looked up as a class name. */
    @Test
    fun testTopLevelOptionalStaysOutsideTheType() {
        assertTagType(
            """
            ---@type string?
            local x
            """.trimIndent(),
            LuaCatsTypeTag::class.java,
            "string",
        )
    }

    @Test
    fun testDoubleOptionalIsStillAnError() {
        doTest(
            """
            ---@class P
            ---@field p {a:string??}
            local P = {}
            """.trimIndent(),
            expectErrors = true,
        )
    }

    /** BUG-545: a `fun` return was a single type, so a parenthesised list failed at its first `,` or `?`. */
    @Test
    fun testParenReturnListInTableType() {
        assertTagType(
            """
            ---@alias Q3 {load: fun():(string?, number?)}
            local x = 1
            """.trimIndent(),
            LuaCatsAliasTag::class.java,
            "{load: fun():(string?, number?)}",
        )
    }

    @Test
    fun testParenReturnListWithoutOptionals() {
        assertTagType(
            """
            ---@alias Q4 {load: fun():(string, number)}
            local x = 1
            """.trimIndent(),
            LuaCatsAliasTag::class.java,
            "{load: fun():(string, number)}",
        )
    }

    @Test
    fun testParenReturnListAtTopLevel() {
        assertTagType(
            """
            ---@type {modname: string, load: fun():(string?, string?)}[]
            local x
            """.trimIndent(),
            LuaCatsTypeTag::class.java,
            "{modname: string, load: fun():(string?, string?)}[]",
        )
    }

    @Test
    fun testEmptyReturnListIsStillAnError() {
        doTest(
            """
            ---@class P
            ---@field p {load: fun():()}
            local P = {}
            """.trimIndent(),
            expectErrors = true,
        )
    }

    /** BUG-546: `async` was read as a type name, and the `fun` keyword after it could not be prose. */
    @Test
    fun testAsyncFunType() {
        assertTagType(
            """
            ---@alias TF async fun(t:table, o:table)
            local x = 1
            """.trimIndent(),
            LuaCatsAliasTag::class.java,
            "async fun(t:table, o:table)",
        )
    }

    /** The prefix is unpinned, so a type named `async` alone still rolls back to `namedType`. */
    @Test
    fun testAsyncAloneIsStillATypeName() {
        assertTagType(
            """
            ---@type async
            local x
            """.trimIndent(),
            LuaCatsTypeTag::class.java,
            "async",
        )
    }

    @Test
    fun testAsyncWithoutParameterListIsStillAnError() {
        doTest(
            """
            ---@alias TF async fun
            local x = 1
            """.trimIndent(),
            expectErrors = true,
        )
    }

    /** BUG-547: a `fun` parameter given by type alone was read as a name, and `:` was demanded. */
    @Test
    fun testUnnamedFunParameter() {
        assertTagType(
            """
            ---@type fun(table)
            local h
            """.trimIndent(),
            LuaCatsTypeTag::class.java,
            "fun(table)",
        )
    }

    /** The lazy.nvim form. `doTest` only: the trailing `[]` after `(...)` is still prose. */
    @Test
    fun testUnnamedFunParameterInCast() {
        doTest(
            """
            local builders
            ---@cast builders (string|fun(LazyPlugin))[]
            print(builders)
            """.trimIndent(),
        )
    }

    @Test
    fun testParameterWithColonButNoTypeIsStillAnError() {
        doTest(
            """
            ---@type fun(a:)
            local h
            """.trimIndent(),
            expectErrors = true,
        )
    }

    /** BUG-514: TAG_RETURN had no string rule, so a string-literal return type was a parse error. */
    @Test
    fun testStringLiteralUnionReturn() {
        assertTagType(
            """
            ---@return "nil"|"boolean"|"number"
            function f1(v) end
            """.trimIndent(),
            LuaCatsReturnTypeDescriptor::class.java,
            "\"nil\"|\"boolean\"|\"number\"",
        )
    }

    /** BUG-514: TAG_TYPE did not lex `...`, and `argumentName` did not admit it. */
    @Test
    fun testVarargFunParameter() {
        doTest(
            """
            ---@overload fun(repl: fun(...: any): string): string
            function f4(r) end
            """.trimIndent(),
        )
    }

    @Test
    fun testUntypedVarargFunParameter() {
        assertTagType(
            """
            ---@type fun(...)
            local f
            """.trimIndent(),
            LuaCatsTypeTag::class.java,
            "fun(...)",
        )
    }

    @Test
    fun testTwoDotVarargIsStillAnError() {
        doTest(
            """
            ---@type fun(..: any)
            local f
            """.trimIndent(),
            expectErrors = true,
        )
    }

    @Test
    fun testUnterminatedReturnStringIsStillAnError() {
        doTest(
            """
            ---@return "nil
            function f1(v) end
            """.trimIndent(),
            expectErrors = true,
        )
    }

    /** BUG-543: the key branch of `fieldDescriptor` had no optional marker, unlike the name branch. */
    @Test
    fun testOptionalBracketKeyFieldName() {
        assertTagType(
            """
            ---@class S
            ---@field [2]? string|fun():string?|false rhs
            local S = {}
            """.trimIndent(),
            LuaCatsFieldTag::class.java,
            "string|fun():string?|false",
        )
    }

    @Test
    fun testDoubleMarkedBracketKeyIsStillAnError() {
        doTest(
            """
            ---@class S
            ---@field [2]?? string
            local S = {}
            """.trimIndent(),
            expectErrors = true,
        )
    }
}
