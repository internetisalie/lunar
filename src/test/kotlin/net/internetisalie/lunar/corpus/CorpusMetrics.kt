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
    /**
     * MAINT-35-02 — files where `luac` accepts at the pinned level and Lunar does not. **Gated.**
     * Only this direction: the reverse has a systematic false positive, because Lunar parses a
     * superset of any single level and defers enforcement to `LuaLanguageLevelInspection` (DR-01).
     */
    val oracleDisagreements: Int = 0,
    /** Diagnostic, capped at 20 in `CorpusSweep.run`: `falseReject:<path>` / `falseAccept:<path>`. */
    val oracleSites: List<String> = emptyList(),
    /** Diagnostic. A timing-out oracle judges nothing, so a non-zero value is a loud warning. */
    val oracleTimeouts: Int = 0,
    /** MAINT-35-04 — token texts did not reconstitute the source. **Gated.** */
    val lexerRoundTripFailures: Int = 0,
    /** MAINT-35-04 — internal tokens escaped the merge; BUG-392's signature. **Gated.** */
    val unmergedTokens: Int = 0,
    /** MAINT-35-05 — `lex:<Class>` / `parse:<Class>` → count. **Gated per key.** */
    val crashes: Map<String, Int> = emptyMap(),
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
 * One ballast group: how many of its files the IDE claims, and how many it does not.
 *
 * Split per member rather than flagged for the whole group. An all-or-nothing flag let a single
 * unrecognised file hide every claimed sibling: luacheck's `spec/folder/rockspec` — extensionless,
 * so [CorpusSweep.groupKey] files it under the same `rockspec` key as 53 real `*.rockspec` — turned
 * the whole group unclaimed, reading as "Lunar claims no rockspecs" when it claims all but one.
 * That defeats the point of an inventory whose job is surfacing integration candidates.
 */
data class BallastGroup(val claimed: Int, val unclaimed: Int) {
    val count: Int get() = claimed + unclaimed
}

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

    /** Key prefix for crash counts: `crash.lex:<Class>` / `crash.parse:<Class>` (MAINT-35-05). */
    const val CRASH_PREFIX = "crash."

    /** Repeated diagnostic key for oracle disagreement sites (MAINT-35-02). */
    const val ORACLE_SITE_KEY = "oracleSite"

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
        // Up to two lines per key: a group with a mixed disposition reports both, so a claimed
        // majority is never hidden by one unclaimed sibling.
        metrics.ballast.toSortedMap().forEach { (key, group) ->
            if (group.claimed > 0) appendLine("${BALLAST_PREFIX}claimed.$key=${group.claimed}")
            if (group.unclaimed > 0) appendLine("${BALLAST_PREFIX}unclaimed.$key=${group.unclaimed}")
        }
        metrics.crashes.toSortedMap().forEach { (key, count) -> appendLine("$CRASH_PREFIX$key=$count") }
        appendLine("oracleDisagreements=${metrics.oracleDisagreements}")
        appendLine("oracleTimeouts=${metrics.oracleTimeouts}")
        appendLine("lexerRoundTripFailures=${metrics.lexerRoundTripFailures}")
        appendLine("unmergedTokens=${metrics.unmergedTokens}")
        metrics.parseErrorFiles.forEach { appendLine("parseErrorFile=$it") }
        // Already capped at construction (CorpusSweep.run), never here: a render-time cap is lossy
        // and would break BaselineRatchetTest.renderParseRoundTrip.
        metrics.oracleSites.forEach { appendLine("$ORACLE_SITE_KEY=$it") }
    }

    fun parse(text: String): CorpusMetrics {
        val rows = text.lineSequence()
            .filter { it.contains('=') && !it.startsWith("#") }
            .map { it.substringBefore('=') to it.substringAfter('=') }
            .toList()
        val scalars = rows
            .filterNot { it.first == "parseErrorFile" }
            .filterNot { it.first == ORACLE_SITE_KEY }
            .filterNot { it.first.startsWith(CRASH_PREFIX) }
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
            // Folded, not associated: a mixed group contributes a claimed *and* an unclaimed row
            // under the same key, and `associate` would keep only the last.
            ballast = rows
                .filter { it.first.startsWith(BALLAST_PREFIX) }
                .fold(mutableMapOf<String, BallastGroup>()) { acc, (key, value) ->
                    val (groupKey, claimed) = parseBallastKey(key)
                    val running = acc[groupKey] ?: BallastGroup(0, 0)
                    acc[groupKey] = if (claimed) {
                        running.copy(claimed = running.claimed + value.toInt())
                    } else {
                        running.copy(unclaimed = running.unclaimed + value.toInt())
                    }
                    acc
                },
            oracleDisagreements = scalars["oracleDisagreements"]?.toInt() ?: 0,
            oracleSites = rows.filter { it.first == ORACLE_SITE_KEY }.map { it.second },
            oracleTimeouts = scalars["oracleTimeouts"]?.toInt() ?: 0,
            lexerRoundTripFailures = scalars["lexerRoundTripFailures"]?.toInt() ?: 0,
            unmergedTokens = scalars["unmergedTokens"]?.toInt() ?: 0,
            crashes = rows
                .filter { it.first.startsWith(CRASH_PREFIX) }
                .associate { it.first.removePrefix(CRASH_PREFIX) to it.second.toInt() },
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
        // Crashes gate PER KEY, like inspections: a new StackOverflowError appearing while an
        // AssertionError disappears must not net to zero.
        val crashKeys = (baseline.crashes.keys + observed.crashes.keys).sorted()
        val gated = listOf(
            Triple("parseErrors", baseline.parseErrors, observed.parseErrors),
            Triple("unresolvedRequires", baseline.unresolvedRequires, observed.unresolvedRequires),
            Triple("oracleDisagreements", baseline.oracleDisagreements, observed.oracleDisagreements),
            Triple(
                "lexerRoundTripFailures",
                baseline.lexerRoundTripFailures,
                observed.lexerRoundTripFailures,
            ),
            Triple("unmergedTokens", baseline.unmergedTokens, observed.unmergedTokens),
        ) + crashKeys.map { key ->
            Triple("$CRASH_PREFIX$key", baseline.crashes[key] ?: 0, observed.crashes[key] ?: 0)
        } + gatedInspectionIds.map { id ->
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
