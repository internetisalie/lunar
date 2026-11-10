package net.internetisalie.lunar.analysis.conformance

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.TreeMap

/** MAINT-55 TC-8: the ratchet of design §3.5. */
@RunWith(JUnit4::class)
class ConformanceRatchetTest {
    private fun results(outcomes: Map<String, Pair<String, String>>): ConformanceResults {
        val cases = TreeMap<String, CaseResult>()
        outcomes.forEach { (id, pair) ->
            cases[id] =
                CaseResult("nil.check", pair.first, pair.second, emptyList(), emptyList(), emptyList())
        }
        return ConformanceResults(ConformanceResults.SCHEMA, "c", "5.4", cases, ConformanceResultsJson.tally(cases))
    }

    private fun same(outcome: String) = outcome to outcome

    @Test
    fun testViolationsAndNotes_TC_8() {
        val baseline =
            results(
                mapOf(
                    "a" to same("agree"),
                    "b" to same("missed"),
                    "c" to same("missed"),
                    "d" to ("missed" to "deviation"),
                    "f" to same("missed"),
                    "g" to same("agree"),
                ),
            )
        val actual =
            results(
                mapOf(
                    "a" to same("missed"),
                    "b" to same("agree"),
                    "c" to same("false-report"),
                    "d" to ("agree" to "deviation-broken"),
                    "e" to same("agree"),
                    "f" to ("missed" to "deviation"),
                ),
            )
        assertEquals(
            listOf(
                "regressed a: agree -> missed",
                "improved b: missed -> agree; record",
                "deviation broken d: observed agree",
                "new case e; record",
                "ruling not recorded f; record",
                "removed case g; record",
            ),
            ConformanceRatchet.violations(baseline, actual),
        )
        assertEquals(listOf("changed c: missed -> false-report"), ConformanceRatchet.notes(baseline, actual))
    }
}
