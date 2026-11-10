package net.internetisalie.lunar.analysis.conformance

/** MAINT-55 design §3.2: a case's outcome from its findings, and after its deviation. */
object ConformanceScorer {
    fun observed(
        case: ConformanceCase,
        findings: List<ConformanceFinding>,
    ): ConformanceOutcome {
        val marked = case.must + case.may
        val unmatched = case.must.filter { line -> findings.none { it.covers(line) } }
        val stray = findings.filter { finding -> marked.none { finding.covers(it) } }
        return when {
            unmatched.isEmpty() && stray.isEmpty() -> ConformanceOutcome.AGREE
            unmatched.isNotEmpty() && stray.isNotEmpty() -> ConformanceOutcome.MISPLACED
            unmatched.isNotEmpty() -> ConformanceOutcome.MISSED
            else -> ConformanceOutcome.FALSE_REPORT
        }
    }

    fun outcome(
        observed: ConformanceOutcome,
        deviation: ConformanceDeviation?,
    ): ConformanceOutcome =
        when {
            deviation == null -> observed
            deviation.expect == observed -> ConformanceOutcome.DEVIATION
            else -> ConformanceOutcome.DEVIATION_BROKEN
        }
}
