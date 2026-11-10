package net.internetisalie.lunar.analysis.conformance

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.TreeMap

/** MAINT-55 TC-17: `bugs.json` against a run's results (design §3.7). */
@RunWith(JUnit4::class)
class ConformanceBugsTest {
    @Test
    fun testMisfiledAndUnfiledCases_TC_17() {
        val cases = TreeMap<String, CaseResult>()
        mapOf("a" to "missed", "b" to "agree", "c" to "misplaced").forEach { (id, outcome) ->
            cases[id] = CaseResult("nil.check", outcome, outcome, emptyList(), emptyList(), emptyList())
        }
        val results =
            ConformanceResults(ConformanceResults.SCHEMA, "c", "5.4", cases, ConformanceResultsJson.tally(cases))

        assertEquals(
            listOf("b is filed under BUG-1 but is agree", "unfiled c"),
            ConformanceBugs.problems(results, mapOf("BUG-1" to listOf("a", "b"))),
        )
    }
}
