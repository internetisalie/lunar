package net.internetisalie.lunar.analysis.conformance

/**
 * MAINT-55 design §3.5: what a run must record or fix. A known disagreement that persists, or that
 * changes kind, is a note — never a violation (MAINT-55-04).
 */
object ConformanceRatchet {
    private val AGREE = ConformanceOutcome.AGREE.json
    private val DEVIATION = ConformanceOutcome.DEVIATION.json
    private val DEVIATION_BROKEN = ConformanceOutcome.DEVIATION_BROKEN.json

    private data class Line(
        val violation: Boolean,
        val text: String,
    )

    fun violations(
        baseline: ConformanceResults,
        actual: ConformanceResults,
    ): List<String> = lines(baseline, actual).filter { it.violation }.map { it.text }

    fun notes(
        baseline: ConformanceResults,
        actual: ConformanceResults,
    ): List<String> = lines(baseline, actual).filterNot { it.violation }.map { it.text }

    private fun lines(
        baseline: ConformanceResults,
        actual: ConformanceResults,
    ): List<Line> =
        (baseline.cases.keys + actual.cases.keys).sorted().flatMap { id ->
            val before = baseline.cases[id]
            val after = actual.cases[id]
            when {
                before == null -> listOf(Line(true, "new case $id; record"))
                after == null -> listOf(Line(true, "removed case $id; record"))
                else -> caseLines(id, before.outcome, after)
            }
        }

    private fun caseLines(
        id: String,
        before: String,
        after: CaseResult,
    ): List<Line> {
        val now = after.outcome
        val violations =
            listOfNotNull(
                "regressed $id: $AGREE -> $now".takeIf { before == AGREE && now != AGREE },
                "deviation broken $id: observed ${after.observed}".takeIf { now == DEVIATION_BROKEN },
                "improved $id: $before -> $AGREE; record".takeIf {
                    before != AGREE &&
                        before != DEVIATION_BROKEN &&
                        now == AGREE
                },
                "ruling not recorded $id; record".takeIf { now == DEVIATION && before != DEVIATION },
            )
        if (violations.isNotEmpty()) return violations.map { Line(true, it) }
        return if (now != before) listOf(Line(false, "changed $id: $before -> $now")) else emptyList()
    }
}
