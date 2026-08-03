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
    /**
     * MAINT-33-10 — the symbols each inspection fired on most often, keyed `<toolId>.<symbol>`.
     * Diagnostic, so **reported, never gated**: it exists to tell a missing-definitions problem
     * (one huge bucket, e.g. `wx`) from a resolution defect (a long tail of names the plugin
     * should already resolve). Capped per inspection so the baseline stays reviewable.
     */
    val symbolHits: Map<String, Int> = emptyMap(),
    /**
     * MAINT-33-07 — every file the sweep did *not* index, grouped by extension or base name and
     * marked by whether any registered file type claims it. Reported, never gated: a new unclaimed
     * group is a discovery, not a regression.
     */
    val ballast: Map<String, BallastGroup> = emptyMap(),
) {
    companion object {
        /** Reserved key for highlights whose originating inspection cannot be identified. */
        const val UNATTRIBUTED = "unattributed"

        /**
         * Reserved key counting files whose highlight pass threw (BUG-390 overflows the stack in
         * the type engine). **Always gated**, so the sweep survives a pathological file without the
         * failure becoming invisible — and while it is non-zero the other per-inspection keys are
         * advisory, because a partial highlight pass makes their counts non-reproducible.
         */
        const val HIGHLIGHT_FAILURES = "highlightFailures"
    }
}

/**
 * One ballast group: how many files share the key, and whether the IDE claims that file name.
 *
 * A group is [claimed] only when *every* member is claimed — the conservative direction, since the
 * inventory exists to surface integration candidates.
 */
data class BallastGroup(val count: Int, val claimed: Boolean)

/** A ratchet verdict: what got worse (fails the test) and what got better (prompts a re-record). */
data class CorpusComparison(
    val regressions: List<String>,
    val improvements: List<String>,
)

private fun Int?.orZero(): Int = this ?: 0

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

    /** Key prefix for ballast groups: `ballast.<claimed|unclaimed>.<groupKey>`. */
    const val BALLAST_PREFIX = "ballast."

    /** Key prefix for the per-symbol breakdown: `symbol.<toolId>.<symbol>`. */
    const val SYMBOL_PREFIX = "symbol."

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
        metrics.symbolHits.toSortedMap().forEach { (key, count) ->
            appendLine("$SYMBOL_PREFIX$key=$count")
        }
        metrics.ballast.toSortedMap().forEach { (key, group) ->
            val flag = if (group.claimed) "claimed" else "unclaimed"
            appendLine("$BALLAST_PREFIX$flag.$key=${group.count}")
        }
        metrics.parseErrorFiles.forEach { appendLine("parseErrorFile=$it") }
    }

    fun parse(text: String): CorpusMetrics {
        val rows = text.lineSequence()
            .filter { it.contains('=') && !it.startsWith("#") }
            .map { it.substringBefore('=') to it.substringAfter('=') }
            .toList()
        val scalars = rows
            .filterNot { it.first == "parseErrorFile" }
            .filterNot { it.first.startsWith(INSPECTION_PREFIX) }
            .filterNot { it.first.startsWith(SYMBOL_PREFIX) }
            .filterNot { it.first.startsWith(BALLAST_PREFIX) }
            .toMap()
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
            symbolHits = rows
                .filter { it.first.startsWith(SYMBOL_PREFIX) }
                .associate { it.first.removePrefix(SYMBOL_PREFIX) to it.second.toInt() },
            ballast = rows
                .filter { it.first.startsWith(BALLAST_PREFIX) }
                .associate { (key, value) ->
                    val (groupKey, claimed) = parseBallastKey(key)
                    groupKey to BallastGroup(value.toInt(), claimed)
                },
        )
    }

    /**
     * Positional, never `split('.')`: the group key may itself contain or begin with a dot, so
     * `ballast.unclaimed..luacov` decomposes to flag `unclaimed` and key `.luacov`.
     */
    private fun parseBallastKey(key: String): Pair<String, Boolean> {
        val body = key.removePrefix(BALLAST_PREFIX)
        val flag = body.substringBefore('.')
        require(flag == "claimed" || flag == "unclaimed") { "Malformed ballast key: $key" }
        return body.substring(flag.length + 1) to (flag == "claimed")
    }

    /**
     * Only defect counters are gated. `commit`, `files` and `requires` are identity-checked by the
     * caller ([CorpusGuards.assertRatchet]) and never appear here: a change in any of them
     * means the pinned tree moved or recognition coverage shifted, which makes every remaining
     * number incomparable rather than merely worse.
     */
    fun compare(baseline: CorpusMetrics, observed: CorpusMetrics): CorpusComparison {
        val inspectionIds = (baseline.inspectionHits.keys + observed.inspectionHits.keys).sorted()
        // BUG-390 makes the inspection counts NON-REPRODUCIBLE: a StackOverflowError is
        // stack-depth dependent, so which files abort mid-highlight varies run to run, and every
        // count moves with it (measured: luarocks LuaTypeAssignability 1252 → 1280 across two runs
        // of identical code). Gating them would be flaky, and a flaky gate gets disabled, so the
        // per-inspection keys stay ADVISORY until a corpus highlights cleanly. `highlightFailures`
        // itself is always gated — it is the thing that must come down.
        val stable = baseline.inspectionHits[CorpusMetrics.HIGHLIGHT_FAILURES].orZero() == 0 &&
            observed.inspectionHits[CorpusMetrics.HIGHLIGHT_FAILURES].orZero() == 0
        val gatedInspectionIds = if (stable) inspectionIds else listOf(CorpusMetrics.HIGHLIGHT_FAILURES)
        val gated = listOf(
            Triple("parseErrors", baseline.parseErrors, observed.parseErrors),
            Triple("unresolvedRequires", baseline.unresolvedRequires, observed.unresolvedRequires),
        ) + gatedInspectionIds.map { id ->
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
