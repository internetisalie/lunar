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
 * BUG-419's ERROR path, in the shapes that actually load it.
 *
 * After BUG-419, `declaredDemand` is the single gate between a diagnostic and a demoted hypothesis,
 * and it has already shipped one defect: the flag was lost travelling from a `@param` use node
 * through `checkFunctionCompatibility`'s argument→parameter wiring, silently demoting *every*
 * declared-contract violation reached through a call. That was caught by a bug report's acceptance
 * criterion, not by a test — one fixture existed and it happened to be the case that still worked.
 *
 * These pin the rest of the path. Every one was mutation-proved on 2026-08-07 — each of the tier
 * split, the three annotation injection sites, the operator sites, and variable-mediated resolution
 * was flipped in turn and confirmed to take a named fixture below red.
 *
 * The flag is read one hop deep, off the variable's own down-set (BUG-97). That is exact because
 * down-sets are transitively closed: a declared use reachable through a chain of variables is in
 * the first variable's down-set too. `LuaTypeGraphClosureWalkTest` pins it against the recursive
 * walk it replaced.
 */
@RunWith(JUnit4::class)
class LuaDeclaredContractErrorTest : BasePlatformTestCase() {
    private fun errorsFor(source: String): List<ElementError> =
        LuaTypesSnapshot
            .forFile(myFixture.configureByText("t.lua", source) as LuaFile)
            .getErrors()

    private fun assertErrors(
        why: String,
        errors: List<ElementError>,
    ) = assertTrue(
        "$why — was: ${errors.map { "${it.severity}:${it.message}" }}",
        errors.any { it.severity == ErrorSeverity.ERROR },
    )

    /** The contract met directly: no variable hop at all, so nothing can drop the flag. */
    @Test
    fun testDeclaredParamRejectsALiteralArgument() {
        assertErrors(
            "a literal violating a declared @param is a contract violation",
            errorsFor(
                """
                ---@param n number
                local function count(n) return n end
                count("s")
                """.trimIndent(),
            ),
        )
    }

    /**
     * A value that reaches the contract through two named variables.
     *
     * A precisely-typed value flows to the `@param` use node directly, so the variable chain is not
     * what carries the demand here. The check lands on a variable only in the nil case below.
     */
    @Test
    fun testDeclaredParamRejectsAValueReachedThroughTwoVariables() {
        assertErrors(
            "a declared @param must still be enforced through a chain of variables",
            errorsFor(
                """
                ---@param n number
                local function count(n) return n end
                local first = "s"
                local second = first
                count(second)
                """.trimIndent(),
            ),
        )
    }

    /**
     * The variable-mediated case: `nil` is checked at the **variable**, not at the value (BUG-416's
     * certainty rule), so the pair `checkTypes` examines is (value, *variable*) and `VariableNode`
     * inherits `UseNode`'s `false` default unless the flag is resolved through the chain. That is
     * the defect BUG-419 shipped, and this is what catches it.
     *
     * The value propagates all the way into the parameter variable, where the `@param` use node is
     * one hop away.
     */
    @Test
    fun testDeclaredParamRejectsNilReachedThroughTwoVariables() {
        assertErrors(
            "a declared @param must be enforced through a chain of variables, not just one hop",
            errorsFor(
                """
                ---@param n number
                local function count(n) return n end
                local first = nil
                local second = first
                count(second)
                """.trimIndent(),
            ),
        )
    }

    /** `@type` is a contract on the variable itself — a different injection site to `@param`. */
    @Test
    fun testDeclaredTypeAnnotationRejectsAConflictingWrite() {
        assertErrors(
            "a write conflicting with a declared @type is a contract violation",
            errorsFor(
                """
                ---@type number
                local total = "s"
                """.trimIndent(),
            ),
        )
    }

    /** `@return` is the third injection site, and the only one whose demand faces backwards. */
    @Test
    fun testDeclaredReturnRejectsAConflictingReturn() {
        assertErrors(
            "a returned value conflicting with a declared @return is a contract violation",
            errorsFor(
                """
                ---@return number
                local function total() return "s" end
                """.trimIndent(),
            ),
        )
    }

    /**
     * Same file, but a GLOBAL function rather than a `local` one — the discriminator for the
     * cross-file fixture below. Definitions files declare globals, so if the contract is already
     * lost here the file boundary is not what breaks it.
     */
    @Test
    fun testDeclaredParamOnAGlobalFunctionStillErrors() {
        assertErrors(
            "a @param on a global function is no less declared than one on a local",
            errorsFor(
                """
                ---@param n number
                function count(n) end
                count("s")
                """.trimIndent(),
            ),
        )
    }

    /**
     * BUG-425. A contract declared in a *definitions file* rather than beside the call produced no
     * diagnostic of any kind — not a demoted hypothesis, nothing — because a declaration-typed
     * callee raised no demands at all.
     *
     * The library-member form, which is what a definitions file generates and what TARGET-10 will
     * emit ~10 000 of.
     */
    @Test
    fun testDeclaredParamInADefinitionsFileStillErrors() {
        myFixture.addFileToProject(
            "defs.lua",
            """
            Lib = {}
            ---@param n number
            function Lib.count(n) end
            """.trimIndent(),
        )
        assertErrors(
            "a @param declared in another file is no less declared",
            errorsFor("""Lib.count("s")"""),
        )
    }

    /** …and a conforming call stays silent. The above must not be "always errors". */
    @Test
    fun testAConformingCrossFileCallIsSilent() {
        myFixture.addFileToProject(
            "defs.lua",
            """
            Lib = {}
            ---@param n number
            function Lib.count(n) end
            """.trimIndent(),
        )
        val errors = errorsFor("""Lib.count(2)""")
        assertTrue(
            "a conforming call must produce no error, got: ${errors.map { "${it.severity}:${it.message}" }}",
            errors.none { it.severity == ErrorSeverity.ERROR },
        )
    }
}
