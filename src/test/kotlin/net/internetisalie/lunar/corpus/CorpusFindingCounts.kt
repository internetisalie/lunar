package net.internetisalie.lunar.corpus

/**
 * MAINT-40-02. The single derivation of every [CorpusMetrics] counter from a findings list, so the
 * baseline's numbers and the stored findings can never disagree.
 */
internal object CorpusFindingCounts {
    /** The baseline must stay a reviewable diff, not a dump of every name in the corpus. */
    private const val SYMBOLS_PER_INSPECTION = 10

    /** Mirrors [SYMBOLS_PER_INSPECTION]'s intent: the baseline must stay reviewable. */
    const val ORACLE_SITES_CAP = 20

    private const val P = CorpusBaseline.INSPECTION_PREFIX

    /**
     * Every counter the findings determine. The identity and inventory fields (`commit`, `files`,
     * `requires`, `ballast`, `libraries`) are left empty for the caller to set.
     */
    fun derive(findings: List<CorpusFinding>): CorpusMetrics {
        val symbols = symbolHits(findings)
        return CorpusMetrics(
            commit = "",
            files = 0,
            parseErrors = count(findings, CorpusRules.PARSE_ERROR),
            requires = 0,
            unresolvedRequires = count(findings, CorpusRules.UNRESOLVED_REQUIRE),
            parseErrorFiles = parseErrorFiles(findings),
            inspectionHits = inspectionHits(findings),
            symbolHits = topSymbols(symbols),
            oracleDisagreements = count(findings, CorpusRules.ORACLE_FALSE_REJECT),
            oracleFalseAccepts = count(findings, CorpusRules.ORACLE_FALSE_ACCEPT),
            oracleSites = oracleSites(findings),
            oracleTimeouts = count(findings, CorpusRules.ORACLE_TIMEOUT),
            lexerRoundTripFailures = count(findings, CorpusRules.LEXER_ROUND_TRIP),
            unmergedTokens = count(findings, CorpusRules.UNMERGED_TOKENS),
            crashes = crashes(findings),
            allSymbolHits = symbols.toSortedMap(),
            findings = findings,
        )
    }

    /** Σ [CorpusFinding.count] over the findings of [rule]. */
    fun count(
        findings: List<CorpusFinding>,
        rule: String,
    ): Int = findings.filter { it.rule == rule }.sumOf { it.count }

    /**
     * Per-inspection counts, plus [CorpusMetrics.HIGHLIGHT_FAILURES] and [CorpusMetrics.UNATTRIBUTED].
     * Unattributed highlights are netted **per file** against that file's parse errors, floored at
     * zero, because every `PsiErrorElement` also yields a null-id ERROR info already gated by
     * `parseErrors` (MAINT-33-06).
     */
    fun inspectionHits(findings: List<CorpusFinding>): Map<String, Int> {
        val hits = mutableMapOf<String, Int>()
        findings.filter { it.rule.startsWith(P) }.forEach { finding ->
            val toolId = finding.rule.removePrefix(P)
            hits[toolId] = (hits[toolId] ?: 0) + finding.count
        }
        val failures = count(findings, CorpusRules.HIGHLIGHT_FAILURE)
        if (failures > 0) hits[CorpusMetrics.HIGHLIGHT_FAILURES] = failures
        val unattributed = findings.groupBy { it.path }.values.sumOf { unattributedIn(it) }
        if (unattributed > 0) hits[CorpusMetrics.UNATTRIBUTED] = unattributed
        return hits
    }

    /** Uncapped `<toolId>.<symbol>` → count, from inspection findings only (MAINT-33-10). */
    fun symbolHits(findings: List<CorpusFinding>): Map<String, Int> {
        val symbols = mutableMapOf<String, Int>()
        findings.filter { it.rule.startsWith(P) }.forEach { finding ->
            val symbol = finding.symbol ?: return@forEach
            val key = "${finding.rule.removePrefix(P)}.$symbol"
            symbols[key] = (symbols[key] ?: 0) + finding.count
        }
        return symbols
    }

    /** `lex:<Class>` / `parse:<Class>` → number of files that crashed that way (MAINT-35-05). */
    fun crashes(findings: List<CorpusFinding>): Map<String, Int> =
        findings.filter { it.rule == CorpusRules.CRASH }.groupingBy { it.message }.eachCount()

    /**
     * Disagreement sites, **gated direction first**, capped so the baseline stays reviewable.
     *
     * The order is load-bearing, not cosmetic. A plain `.sorted().take(20)` sorts `falseAccept`
     * ahead of `falseReject` alphabetically, so the moment a corpus produced more than 20 sites the
     * cap would discard exactly the direction that fails the build — leaving `oracleDisagreements`
     * non-zero with nothing in the baseline saying where.
     */
    fun oracleSites(findings: List<CorpusFinding>): List<String> {
        val rejects = sitesOf(findings, CorpusRules.ORACLE_FALSE_REJECT, "falseReject")
        val accepts = sitesOf(findings, CorpusRules.ORACLE_FALSE_ACCEPT, "falseAccept")
        return (rejects + accepts).take(ORACLE_SITES_CAP)
    }

    /**
     * `<path>:<line>:<message>` for each parse error, in list order, so a parse regression is
     * locatable from the baseline diff alone instead of needing a bisect over the file (BUG-392).
     */
    fun parseErrorFiles(findings: List<CorpusFinding>): List<String> =
        findings.filter { it.rule == CorpusRules.PARSE_ERROR }.map { "${it.path}:${it.line}:${it.message}" }

    private fun unattributedIn(perFile: List<CorpusFinding>): Int {
        val unattributed = count(perFile, CorpusRules.UNATTRIBUTED_HIGHLIGHT) - count(perFile, CorpusRules.PARSE_ERROR)
        return unattributed.coerceAtLeast(0)
    }

    private fun sitesOf(
        findings: List<CorpusFinding>,
        rule: String,
        label: String,
    ): List<String> = findings.filter { it.rule == rule }.map { "$label:${it.path}" }.sorted()

    /**
     * The top [SYMBOLS_PER_INSPECTION] symbols per inspection. Capped so the baseline stays a
     * reviewable diff rather than a dump of every name in the corpus; ties break by name so the
     * selection is deterministic.
     */
    private fun topSymbols(symbols: Map<String, Int>): Map<String, Int> =
        symbols.entries
            .groupBy { it.key.substringBefore('.') }
            .values
            .flatMap { perTool ->
                perTool
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .take(SYMBOLS_PER_INSPECTION)
            }.associate { it.key to it.value }
}
