package net.internetisalie.lunar.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.ElementError
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-419 defect 4: arity is a contract only when the signature was DECLARED.
 *
 * Lua adjusts arguments to parameters — missing ones become `nil`, extra ones are discarded
 * (Reference Manual §3.4.10). So `function f(a, b)` names two parameters; it does not require two
 * arguments. Only `---@param b T` (without `?`) does.
 *
 * This was the last thing standing between the engine and this report's thesis, and it was invisible
 * for a structural reason: [net.internetisalie.lunar.lang.psi.types.LuaTypeGraph]'s arity check calls
 * `addError` directly instead of `reportIncompatible`, so BUG-419's `declaredDemand` gate — which the
 * whole report is about — never applied to it. Measured at the inspection level across the four
 * corpus members, **629 of the 714** surviving `LuaTypeAssignability` emissions were arity, and 157
 * of them came from one file's `local function H(c, bg)` whose own first body line reads
 * `local bg = bg and H(bg) or {255, 255, 255}`.
 *
 * Both tiers are pinned here, in both directions. Demoting is only correct if the declared case
 * survives it — the failure mode that shipped once already (BUG-419's `declaredDemand` flag lost in
 * `checkFunctionCompatibility`) was a fix that silently swallowed real signal.
 */
@RunWith(JUnit4::class)
class LuaInferredArityTierTest : BasePlatformTestCase() {
    private fun errorsFor(source: String): List<ElementError> =
        LuaTypesSnapshot
            .forFile(myFixture.configureByText("t.lua", source) as LuaFile)
            .getErrors()

    private fun arityErrors(source: String): List<ElementError> =
        errorsFor(source).filter { it.message.startsWith("Too few") || it.message.startsWith("Too many") }

    private fun assertTier(
        why: String,
        expected: ErrorSeverity,
        source: String,
    ) {
        val arity = arityErrors(source)
        assertFalse("$why — no arity emission at all, so the tier is untested", arity.isEmpty())
        assertTrue(
            "$why — was: ${arity.map { "${it.severity}:${it.message}" }}",
            arity.all { it.severity == expected },
        )
    }

    // -------------------------------------------------------------------------------------------
    // Inferred signatures: emitted, but demoted below the inspection's floor.
    // -------------------------------------------------------------------------------------------

    @Test
    fun testUnannotatedUnderApplicationIsAHypothesis() =
        assertTier(
            "a bare parameter list does not require its parameters",
            ErrorSeverity.HYPOTHESIS,
            """
            local function h(c, bg) return c, bg end
            h("8e908c")
            """.trimIndent(),
        )

    @Test
    fun testUnannotatedOverApplicationIsAHypothesis() =
        assertTier(
            "Lua discards extra arguments, so over-application violates nothing either",
            ErrorSeverity.HYPOTHESIS,
            """
            local function f(a) return a end
            f(1, 2)
            """.trimIndent(),
        )

    /**
     * The corpus shape, verbatim in structure: the author handles the missing argument on the
     * function's own first line. 157 emissions in `cfg/tomorrow.lua` had exactly this source.
     */
    @Test
    fun testTheAuthorHandlingNilIsNotADefect() =
        assertTier(
            "a parameter the body defaults is plainly optional",
            ErrorSeverity.HYPOTHESIS,
            """
            local function H(c, bg)
              local bg = bg and H(bg) or {255, 255, 255}
              return c, bg
            end
            H("8e908c")
            """.trimIndent(),
        )

    // -------------------------------------------------------------------------------------------
    // Declared signatures: still contracts. These are what make the demotion safe rather than blind.
    // -------------------------------------------------------------------------------------------

    @Test
    fun testDeclaredParamUnderApplicationStillWarns() =
        assertTier(
            "a declared @param is a contract, and under-applying it is a diagnostic",
            ErrorSeverity.WARNING,
            """
            ---@param a number
            ---@param b number
            local function add(a, b) return a + b end
            add(1)
            """.trimIndent(),
        )

    @Test
    fun testDeclaredParamOverApplicationStillWarns() =
        assertTier(
            "a declared signature bounds the argument count in both directions",
            ErrorSeverity.WARNING,
            """
            ---@param a number
            local function f(a) return a end
            f(1, 2)
            """.trimIndent(),
        )

    /**
     * The other way to write a signature by hand. `---@param` reaches the graph through
     * `LuaTypesVisitor`; a `fun(...)` literal reaches it through `TypeParser`, which is a separate
     * construction site and would otherwise be marked declared with nothing testing it.
     */
    @Test
    fun testDeclaredFunctionTypeAnnotationStillWarns() =
        assertTier(
            "a hand-written fun(...) signature is as much a contract as @param",
            ErrorSeverity.WARNING,
            """
            ---@type fun(a: number, b: number)
            local add
            add(1)
            """.trimIndent(),
        )

    /** `?` is the user saying the parameter is optional — no emission at any tier. */
    @Test
    fun testDeclaredOptionalParamIsNotRequired() =
        assertTrue(
            "an explicitly optional parameter must produce no arity emission at all",
            arityErrors(
                """
                ---@param a number
                ---@param b? number
                local function f(a, b) return a, b end
                f(1)
                """.trimIndent(),
            ).isEmpty(),
        )

    // -------------------------------------------------------------------------------------------
    // The real flow. The snapshot tier is only meaningful if the inspection actually honours it.
    // -------------------------------------------------------------------------------------------

    private fun inspectionDescriptions(text: String): List<String> {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("test.lua", text)
        return myFixture.doHighlighting().mapNotNull { it.description }
    }

    @Test
    fun testInferredArityReachesNoProblemInTheEditor() {
        val descriptions =
            inspectionDescriptions(
                """
                local function h(c, bg) return c, bg end
                h("8e908c")
                """.trimIndent(),
            )
        assertTrue(
            "an inferred-arity hypothesis must not surface as a problem, got: $descriptions",
            descriptions.none { it.startsWith("Too few") || it.startsWith("Too many") },
        )
    }

    @Test
    fun testDeclaredArityStillReachesTheEditor() {
        val descriptions =
            inspectionDescriptions(
                """
                ---@param a number
                ---@param b number
                local function add(a, b) return a + b end
                add(1)
                """.trimIndent(),
            )
        assertTrue(
            "a declared-contract violation must still be shown, got: $descriptions",
            descriptions.any { it.startsWith("Too few arguments") },
        )
    }
}
