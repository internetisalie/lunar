package net.internetisalie.lunar.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-419: incompatibility is a diagnostic only when the expectation is one the USER declared.
 *
 * Against a demand the engine synthesized from usage, both sides are its own inferences, so the
 * conflict says the model is incomplete rather than that the code is wrong. Measured across four
 * corpus members before implementing: 7 430 of 7 433 emissions were inferred-vs-inferred, so this
 * is not an edge case being tidied — it is almost the entire output of the inspection.
 */
@RunWith(JUnit4::class)
class LuaTypeHypothesisTest : BasePlatformTestCase() {
    private fun errorsFor(source: String) =
        LuaTypesSnapshot
            .forFile(myFixture.configureByText("t.lua", source) as LuaFile)
            .getErrors()

    /**
     * The rule must NOT swallow real signal — the report's own acceptance criterion. A declared
     * `@param` contract plus a certain, conflicting value is still an ERROR.
     */
    @Test
    fun testDeclaredContractViolationIsStillAnError() {
        val errors =
            errorsFor(
                """
                ---@param n number
                local function count(n) return n end
                local nothing = nil
                count(nothing)
                """.trimIndent(),
            )
        assertTrue(
            "a declared @param contract with a certain conflicting value must remain an ERROR, was: " +
                errors.map { "${it.severity}:${it.message}" },
            errors.any { it.severity == ErrorSeverity.ERROR },
        )
    }

    /**
     * `f()` synthesizes a `fun()` demand on `f` purely from the call — nobody declared `f` callable. A value conflicting with it is two of the engine's guesses disagreeing, so
     * it must not reach the user as an error; it becomes a hypothesis carrying the annotate-it fix.
     *
     * Contrast an OPERATOR demand (`a .. b` requiring a string): that one is a rule of the Lua
     * language, not an inference, so it stays an ERROR — see LuaTypeAssignabilityInspectionTest.
     */
    @Test
    fun testUsageSynthesizedDemandProducesAHypothesisNotAnError() {
        val errors =
            errorsFor(
                """
                local f = 42
                f()
                """.trimIndent(),
            )
        assertTrue(
            "a usage-synthesized demand must not produce an ERROR, was: " +
                errors.map { "${it.severity}:${it.message}" },
            errors.none { it.severity == ErrorSeverity.ERROR },
        )
        assertTrue(
            "…it must still be recorded as a HYPOTHESIS so the intention has somewhere to hang: " +
                errors.map { "${it.severity}:${it.message}" },
            errors.any { it.severity == ErrorSeverity.HYPOTHESIS },
        )
    }

    /** A hypothesis carries the inferred value type, so the fix can scaffold `---@type` with it. */
    @Test
    fun testHypothesisCarriesTheInferredValueTypeForTheFix() {
        val hypotheses =
            errorsFor(
                """
                local f = 42
                f()
                """.trimIndent(),
            ).filter { it.severity == ErrorSeverity.HYPOTHESIS }
        assertTrue("expected at least one hypothesis", hypotheses.isNotEmpty())
        assertNotNull(
            "the annotate-it fix needs the inferred type to scaffold with",
            hypotheses.first().inferredValueType,
        )
    }

    /** The demoted tier must be invisible to the inspection — that is the whole point. */
    @Test
    fun testHypothesisIsNotReportedByTheInspection() {
        myFixture.configureByText(
            "t.lua",
            """
            local f = 42
            f()
            """.trimIndent(),
        )
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        val problems = myFixture.doHighlighting().filter { it.description != null }
        assertTrue(
            "a hypothesis must not surface as an inspection problem, got: ${problems.map { it.description }}",
            problems.none { it.description?.contains("is not assignable") == true },
        )
    }
}
