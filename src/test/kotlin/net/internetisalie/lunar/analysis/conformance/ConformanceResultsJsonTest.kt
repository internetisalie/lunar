package net.internetisalie.lunar.analysis.conformance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.TreeMap

/** MAINT-55 TC-7: `results.json` renders sorted and round-trips (design §2.5). */
@RunWith(JUnit4::class)
class ConformanceResultsJsonTest {
    private fun result(outcome: String) =
        CaseResult("nil.check", outcome, outcome, listOf(2), emptyList(), listOf("2-4:x"))

    @Test
    fun testRenderIsSortedAndRoundTrips_TC_7() {
        val cases = TreeMap<String, CaseResult>()
        cases["b"] = result("missed")
        cases["a"] = result("agree")
        val results =
            ConformanceResults(
                ConformanceResults.SCHEMA,
                "0".repeat(40),
                "5.4",
                cases,
                ConformanceResultsJson.tally(cases),
            )

        val text = ConformanceResultsJson.render(results)

        assertTrue("text was $text", text.indexOf("\"a\"") in 0 until text.indexOf("\"b\""))
        assertTrue(text.endsWith("\n"))
        assertEquals(results, ConformanceResultsJson.parse(text))
    }

    @Test
    fun testTallyCountsOutcomesPerRule() {
        val cases = mapOf("a" to result("agree"), "b" to result("missed"), "c" to result("missed"))
        assertEquals(mapOf("nil.check" to mapOf("agree" to 1, "missed" to 2)), ConformanceResultsJson.tally(cases))
    }
}
