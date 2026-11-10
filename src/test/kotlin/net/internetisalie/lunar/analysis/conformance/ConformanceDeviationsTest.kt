package net.internetisalie.lunar.analysis.conformance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** MAINT-55 TC-20, TC-21 and TC-6: `deviations.json` by design §2.4. */
@RunWith(JUnit4::class)
class ConformanceDeviationsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun deviation(
        case: String,
        rule: String,
    ) = ConformanceDeviation(case, rule, "p", ConformanceOutcome.MISSED, "r", "BUG-1", "someone", "2026-11-09")

    private fun entryJson(
        case: String,
        override: Pair<String, String>? = null,
    ): String {
        val fields =
            linkedMapOf(
                "case" to case,
                "rule" to "nil.check",
                "policy" to "p",
                "expect" to "missed",
                "reason" to "r",
                "ref" to "BUG-1",
                "decidedBy" to "someone",
                "decidedOn" to "2026-11-09",
            )
        override?.let { fields[it.first] = it.second }
        return fields.entries.joinToString(", ", "{", "}") { (key, value) -> "\"$key\": \"$value\"" }
    }

    private fun loadError(vararg entries: String): String {
        val file = temp.newFile()
        file.writeText("""{"schema": 1, "deviations": [${entries.joinToString(", ")}]}""")
        return assertThrows(IllegalArgumentException::class.java) { ConformanceDeviations.load(file) }.message.orEmpty()
    }

    @Test
    fun testUnknownCaseIsReportedBeforeRule_TC_20() {
        val deviations = mapOf("d1" to deviation("d1", "nil.check"), "d2" to deviation("d2", "arity.tooMany"))
        val resolved = mapOf("d1" to "nil.check", "d3" to "arity.tooMany")
        assertEquals(listOf("deviation d2 is for an unknown case"), ConformanceDeviations.check(deviations, resolved))
    }

    @Test
    fun testRuleMismatchIsReported_TC_21() {
        val deviations = mapOf("d1" to deviation("d1", "nil.check"))
        assertEquals(
            listOf("deviation d1 has rule nil.check but the case resolves to arity.tooMany"),
            ConformanceDeviations.check(deviations, mapOf("d1" to "arity.tooMany")),
        )
    }

    @Test
    fun testLoadRejectsBlankRepeatedAndBadExpect_TC_6() {
        val blank = loadError(entryJson("d1", "decidedBy" to ""))
        assertTrue("message was $blank", blank.contains("decidedBy"))
        val repeated = loadError(entryJson("d7"), entryJson("d7"))
        assertTrue("message was $repeated", repeated.contains("d7"))
        val badExpect = loadError(entryJson("d1", "expect" to "agree"))
        assertTrue("message was $badExpect", badExpect.contains("expect"))
    }

    @Test
    fun testLoadReadsAValidEntry() {
        val file = temp.newFile()
        file.writeText("""{"schema": 1, "deviations": [${entryJson("d1")}]}""")
        assertEquals(mapOf("d1" to deviation("d1", "nil.check")), ConformanceDeviations.load(file))
    }
}
