package net.internetisalie.lunar.analysis.conformance

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** MAINT-55 TC-4 and TC-5: scoring by design §3.2. */
@RunWith(JUnit4::class)
class ConformanceScorerTest {
    private fun case(
        must: Set<Int>,
        may: Set<Int> = emptySet(),
    ) = ConformanceCase("s-001", "nil.check", "s.lua#1", List(5) { "print($it)" }, must, may)

    private fun finding(
        start: Int,
        end: Int,
    ) = ConformanceFinding(start, end, "x")

    @Test
    fun testObservedOutcomes_TC_4() {
        assertEquals(ConformanceOutcome.MISPLACED, ConformanceScorer.observed(case(setOf(4)), listOf(finding(2, 2))))
        assertEquals(ConformanceOutcome.AGREE, ConformanceScorer.observed(case(setOf(3)), listOf(finding(2, 4))))
        assertEquals(
            ConformanceOutcome.FALSE_REPORT,
            ConformanceScorer.observed(case(emptySet()), listOf(finding(1, 1))),
        )
        assertEquals(ConformanceOutcome.MISSED, ConformanceScorer.observed(case(setOf(2)), emptyList()))
        assertEquals(
            ConformanceOutcome.AGREE,
            ConformanceScorer.observed(case(emptySet(), may = setOf(3)), listOf(finding(3, 3))),
        )
    }

    @Test
    fun testDeviationOutcomes_TC_5() {
        val deviation =
            ConformanceDeviation(
                case = "s-001",
                rule = "nil.check",
                policy = "uncertain-nil",
                expect = ConformanceOutcome.MISSED,
                reason = "r",
                ref = "BUG-416",
                decidedBy = "someone",
                decidedOn = "2026-11-09",
            )
        assertEquals(ConformanceOutcome.DEVIATION, ConformanceScorer.outcome(ConformanceOutcome.MISSED, deviation))
        assertEquals(
            ConformanceOutcome.DEVIATION_BROKEN,
            ConformanceScorer.outcome(ConformanceOutcome.AGREE, deviation),
        )
        assertEquals(ConformanceOutcome.AGREE, ConformanceScorer.outcome(ConformanceOutcome.AGREE, null))
    }
}
