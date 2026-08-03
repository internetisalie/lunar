package net.internetisalie.lunar.corpus

import java.io.File

/**
 * What one corpus sweep observed. Nobody hand-authors expectations for ~300 third-party files, so
 * these counts are compared against a recorded baseline instead: the suite fails only when a
 * number gets *worse*.
 */
data class CorpusMetrics(
    val commit: String,
    val files: Int,
    val parseErrors: Int,
    val requires: Int,
    val unresolvedRequires: Int,
    val parseErrorFiles: List<String>,
    /**
     * MAINT-33-06 — warnings per inspection, keyed by [com.intellij.codeInsight.daemon.impl.HighlightInfo.getInspectionToolId].
     * Infos with a null id land under [UNATTRIBUTED], net of the parse errors already counted by
     * [parseErrors] (design §3.3 step 4).
     */
    val inspectionHits: Map<String, Int> = emptyMap(),
) {
    companion object {
        /** Reserved key for highlights whose originating inspection cannot be identified. */
        const val UNATTRIBUTED = "unattributed"

        /**
         * Reserved key counting files whose highlight pass threw (BUG-390 overflows the stack in
         * the type engine). Gated like any other key, so the sweep survives a pathological file
         * without the failure becoming invisible.
         */
        const val HIGHLIGHT_FAILURES = "highlightFailures"
    }
}

/** A ratchet verdict: what got worse (fails the test) and what got better (prompts a re-record). */
data class CorpusComparison(
    val regressions: List<String>,
    val improvements: List<String>,
)

/**
 * Reads and writes `src/test/resources/corpus/<name>.baseline` — a flat, sorted, one-fact-per-line
 * text format chosen over JSON so that a ratchet movement is legible in a review diff.
 */
object CorpusBaseline {

    /**
     * Key prefix for per-inspection counts. The remainder of the key is taken **whole**, including
     * any dots — tool ids are opaque strings, so the decomposition must be positional.
     */
    const val INSPECTION_PREFIX = "inspection."

    fun file(repoRoot: File, name: String): File =
        File(repoRoot, "src/test/resources/corpus/$name.baseline")

    fun render(metrics: CorpusMetrics): String = buildString {
        appendLine("commit=${metrics.commit}")
        appendLine("files=${metrics.files}")
        appendLine("parseErrors=${metrics.parseErrors}")
        appendLine("requires=${metrics.requires}")
        appendLine("unresolvedRequires=${metrics.unresolvedRequires}")
        // Sorted so a ratchet movement is a one-line diff, not a reordering.
        metrics.inspectionHits.toSortedMap().forEach { (id, count) ->
            appendLine("$INSPECTION_PREFIX$id=$count")
        }
        metrics.parseErrorFiles.forEach { appendLine("parseErrorFile=$it") }
    }

    fun parse(text: String): CorpusMetrics {
        val rows = text.lineSequence()
            .filter { it.contains('=') && !it.startsWith("#") }
            .map { it.substringBefore('=') to it.substringAfter('=') }
            .toList()
        val scalars = rows.filterNot { it.first == "parseErrorFile" }.toMap()
        return CorpusMetrics(
            commit = scalars.getValue("commit"),
            files = scalars.getValue("files").toInt(),
            parseErrors = scalars.getValue("parseErrors").toInt(),
            requires = scalars.getValue("requires").toInt(),
            unresolvedRequires = scalars.getValue("unresolvedRequires").toInt(),
            parseErrorFiles = rows.filter { it.first == "parseErrorFile" }.map { it.second },
            inspectionHits = rows
                .filter { it.first.startsWith(INSPECTION_PREFIX) }
                .associate { it.first.removePrefix(INSPECTION_PREFIX) to it.second.toInt() },
        )
    }

    /**
     * Only defect counters are gated. `commit`, `files` and `requires` are identity-checked by the
     * caller ([LuaCorpusSweepTest.assertRatchet]) and never appear here: a change in any of them
     * means the pinned tree moved or recognition coverage shifted, which makes every remaining
     * number incomparable rather than merely worse.
     */
    fun compare(baseline: CorpusMetrics, observed: CorpusMetrics): CorpusComparison {
        val inspectionIds = (baseline.inspectionHits.keys + observed.inspectionHits.keys).sorted()
        val gated = listOf(
            Triple("parseErrors", baseline.parseErrors, observed.parseErrors),
            Triple("unresolvedRequires", baseline.unresolvedRequires, observed.unresolvedRequires),
        ) + inspectionIds.map { id ->
            // A key present on one side only counts as 0 on the other, so an inspection that
            // starts (or stops) firing is a movement rather than a silent no-op.
            Triple(
                "$INSPECTION_PREFIX$id",
                baseline.inspectionHits[id] ?: 0,
                observed.inspectionHits[id] ?: 0,
            )
        }
        return CorpusComparison(
            regressions = gated.filter { it.third > it.second }.map(::describe),
            improvements = gated.filter { it.third < it.second }.map(::describe),
        )
    }

    private fun describe(delta: Triple<String, Int, Int>): String =
        "${delta.first}: baseline ${delta.second} → observed ${delta.third}"
}
