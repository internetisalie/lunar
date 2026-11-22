package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.ElementError
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-116: a mismatch is an ERROR only when the demand it fails is one the user declared (BUG-419).
 *
 * A variable's demand is the merge of every use it reaches, so the pair a call argument is checked
 * against is `(value, parameter variable)`. Reaching one declared `@param` anywhere downstream used
 * to promote every failure of that variable, including failures of members no annotation mentions.
 */
@RunWith(JUnit4::class)
class LuaDeclaredProvenanceTest : BasePlatformTestCase() {
    private fun errorsFor(source: String): List<ElementError> =
        LuaTypesSnapshot
            .forFile(myFixture.configureByText("t.lua", source) as LuaFile)
            .getErrors()

    private val unionArgument =
        """
        local function fin(n) return n.finish + 1 end
        local function parseSimple(node)
            %s
            if node.type == 'x' then return fin(node.node) end
            return fin(node.op)
        end
        local function peekWord() return nil end
        local function parseName()
            local word = peekWord()
            if not word then return nil end
            return { type = 'name', start = 1, finish = 2 }
        end
        local function f()
            local name = parseName()
            if name then parseSimple(name) end
        end
        """.trimIndent()

    private val plainContract =
        """
        ---@class Plain
        ---@param obj Plain
        local function createLocal(obj) return obj end
        """.trimIndent()

    @Test
    fun testAnInferredDemandFailureIsNotPromotedByAnUnrelatedDeclaredParamTheVariableReaches() {
        val errors = errorsFor(plainContract + "\n" + unionArgument.format("createLocal(node)"))
        assertTrue("the fixture must still report the inferred failures: $errors", errors.isNotEmpty())
        assertTrue(
            "nothing here fails a declared demand: ${errors.map { "${it.severity}:${it.message}" }}",
            errors.none { it.severity == ErrorSeverity.ERROR },
        )
    }

    @Test
    fun testAFieldTheClassDeclaresStaysAnErrorAtTheArgument() {
        val errors =
            errorsFor(
                """
                ---@class Strict
                ---@field id integer
                ---@param s Strict
                local function take(s) return s end
                take({})
                """.trimIndent(),
            )
        assertTrue("a declared field the argument lacks: $errors", errors.any { it.severity == ErrorSeverity.ERROR })
    }

    @Test
    fun testAViolationOfTheDeclaredParamThroughAnotherVariableStaysAnError() {
        val errors =
            errorsFor(
                """
                ---@class Strict
                ---@field id integer
                ---@param s Strict
                local function take(s) return s end
                local function pass(node)
                    take(node)
                    return node.extra
                end
                pass({})
                """.trimIndent(),
            )
        assertTrue(
            "the declared `id` is missing, reached through `node`: ${errors.map { "${it.severity}:${it.message}" }}",
            errors.any { it.severity == ErrorSeverity.ERROR },
        )
    }
}
