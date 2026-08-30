package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * BUG-473 — the diagnostics a `---@class` receiver produces, pinned exactly.
 *
 * The correctness claim a performance phase owes is not "the suite is green", it is "the engine
 * still says the same things". BUG-473's fastest measured candidate (dropping the reverse member
 * edge in `checkTableCompatibility`) passed every suite while **deleting** the `---@param`
 * violation on every method call: on [paramViolationFixture] it took 13 diagnostics down to 3 and
 * left `number is not assignable to string` surviving only as [ErrorSeverity.HYPOTHESIS], which
 * BUG-419's rule forbids an inspection from reporting. A green suite did not distinguish that from
 * a correct optimisation, so the multiset is pinned here instead.
 *
 * The expectations are the counts measured on the pre-Phase-2 engine, so this file reads as a
 * before/after comparison of exactly the quantity a performance change must not move.
 */
class LuaAnnotatedClassDiagnosticsTest : BaseDocumentTest() {
    @Test
    fun declaredParamViolationKeepsItsFourErrorTierDiagnostics() {
        assertDiagnostics(
            paramViolationFixture(),
            mapOf(
                "ERROR|number is not assignable to string" to 2,
                "ERROR|string | number is not assignable to string" to 1,
                "ERROR|{ ... } | Builder is not assignable to Builder" to 1,
                "HYPOTHESIS|Builder | { ... } | Builder is not assignable to Builder" to 1,
                "HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(n)" to 2,
                "HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(p)" to 2,
                "HYPOTHESIS|number is not assignable to string" to 1,
                "HYPOTHESIS|{ ... } | Builder is not assignable to Builder" to 2,
                "HYPOTHESIS|{ ... } | Builder is not assignable to { ... }" to 1,
            ),
        )
    }

    @Test
    fun twentyCallSitesEmitTheSameEightyThreeDiagnostics() {
        assertDiagnostics(annotatedFixture(20), expectedForCallSites(20))
    }

    @Test
    fun fortyCallSitesEmitTheSameOneHundredAndSixtyThreeDiagnostics() {
        assertDiagnostics(annotatedFixture(40), expectedForCallSites(40))
    }

    private fun assertDiagnostics(
        source: String,
        expected: Map<String, Int>,
    ) {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            configureByText(source)
            val types = LuaTypesSnapshot.forFile(myFixture.file)
            val actual =
                types
                    .getErrors()
                    .groupingBy { "${it.severity}|${it.message}" }
                    .eachCount()
            assertEquals(expected, actual, "the diagnostics of an annotated-class receiver moved")
        }
    }

    /**
     * Diagnostics are exactly 4n + 3 on this shape, and every one of them is a HYPOTHESIS: the
     * fixture's `setName` declares no parameter type, so no user-written contract is violated.
     */
    private fun expectedForCallSites(callSites: Int): Map<String, Int> =
        mapOf(
            "HYPOTHESIS|Too few arguments: expected at least 1, got 0" to 1,
            "HYPOTHESIS|Too many arguments: expected at most 0, got 1" to 2 * callSites,
            "HYPOTHESIS|fun() | fun(n) is not assignable to fun()" to callSites + 1,
            "HYPOTHESIS|fun(n) | fun() is not assignable to fun()" to 1,
            "HYPOTHESIS|{ ... } | Builder is not assignable to { ... }" to callSites,
        )

    private fun paramViolationFixture(): String =
        """
        |---@class Builder
        |local Builder = {}
        |---@param n string
        |---@return Builder
        |function Builder:setName(n) return self end
        |local b = Builder
        |b:setName(42)
        """.trimMargin()

    private fun annotatedFixture(callSites: Int): String {
        val calls = (0 until callSites).joinToString("\n") { "b:setName(\"a$it\")" }
        return """
            |---@class Builder
            |local Builder = {}
            |function Builder:setName(n) end
            |
            |local b = Builder
            |$calls
            """.trimMargin()
    }
}
