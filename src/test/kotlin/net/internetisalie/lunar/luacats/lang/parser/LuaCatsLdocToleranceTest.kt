package net.internetisalie.lunar.luacats.lang.parser

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * BUG-393: ordinary [LDoc](https://lunarmodules.github.io/ldoc/) constructs must not report syntax
 * errors inside `---` comments.
 *
 * A doc comment Lunar cannot fully model is a *documentation* concern: it should degrade to
 * unparsed prose. Emitting a `PsiErrorElement` marks otherwise-clean Lua as broken. All three
 * constructs below were found by sweeping upstream KOReader with the MAINT-33 machinery.
 */
class LuaCatsLdocToleranceTest : BaseDocumentTest() {
    private fun assertNoParseErrors(code: String) {
        myFixture.configureByText(LuaFileType, code)
        runReadAction {
            val file = myFixture.file
            PsiTreeUtil.findChildrenOfAnyType(file, false, com.intellij.psi.PsiElement::class.java)
            val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
            if (errors.isNotEmpty()) {
                println("PSI TREE FOR:\n$code")
                println(
                    com.intellij.psi.impl.DebugUtil
                        .psiToString(file, true),
                )
                errors.forEach { println("  ERROR: '${it.errorDescription}' at offset ${it.textOffset}") }
            }
            Assertions.assertTrue(
                errors.isEmpty(),
                "LDoc prose must not parse as a syntax error. In:\n$code\nErrors: " +
                    errors.joinToString { "'${it.errorDescription}'" },
            )
        }
    }

    /** LDoc's bracketed modifier marks an optional parameter and may carry a default. */
    @Test
    fun testParamWithBracketedOptModifier() {
        assertNoParseErrors(
            "--- @param[opt=false] explicit boolean  When auto_close is false, set true to close.\n" +
                "function M.closeDB(explicit) end\n",
        )
    }

    /** `@func` is LDoc-only and has no LuaCATS equivalent; an unknown tag must be inert. */
    @Test
    fun testUnknownLdocTagIsInert() {
        assertNoParseErrors(
            "--- @func callback(v1, v2)\n" +
                "function M.arrayContains(t, v, cb) end\n",
        )
    }

    /** A backtick code span inside a description lexes as CODE, which `description` must accept. */
    @Test
    fun testBacktickCodeSpanInParamDescription() {
        assertNoParseErrors(
            "--- @param array Lua table (every value must match the type of `array`)\n" +
                "function M.binarySearch(array, value) end\n",
        )
    }

    /** The same span in a `@return` description, which has its own rule. */
    @Test
    fun testBacktickCodeSpanInReturnDescription() {
        assertNoParseErrors(
            "--- @return boolean true when `value` is present\n" +
                "function M.has(value) end\n",
        )
    }

    /** Regression guard: a well-formed tag must still parse into real PSI, not degrade to prose. */
    @Test
    fun testWellFormedTagsStillParse() {
        assertNoParseErrors(
            "---@class Animal\n---@field name string\n---@param x number\n---@return boolean\n" +
                "function f(x) end\n",
        )
    }
}
