package net.internetisalie.lunar.corpus

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.internetisalie.lunar.lang.LuaLanguageLevel
import java.io.File

/**
 * One pinned fuzzer corpus from `tooling/corpus/torture.json` (MAINT-35-06).
 *
 * Pinned by archive [sha256] rather than a commit, because these are GitHub **release assets** and
 * there is no tree to name. That is a weaker pin than `corpus.json`'s commits — it is exactly why
 * the digest is mandatory rather than advisory.
 */
internal data class TortureMember(
    val name: String,
    val sha256: String,
    val luaLevel: LuaLanguageLevel,
)

internal object TortureManifest {

    const val TORTURE_DIR = "test/corpus-torture"

    private const val MANIFEST_PATH = "tooling/corpus/torture.json"

    fun member(repoRoot: File, name: String): TortureMember {
        val manifest = File(repoRoot, MANIFEST_PATH)
        require(manifest.isFile) { "No torture manifest at $MANIFEST_PATH" }
        val matches = JsonParser.parseString(manifest.readText())
            .asJsonObject.getAsJsonArray("members")
            .map { parseMember(it.asJsonObject) }
            .filter { it.name == name }
        // Same distinction as CorpusManifest.entry: a duplicate must not report as absent, which
        // would send the reader to the fetch script for a manifest problem.
        require(matches.size <= 1) { "Duplicate torture member '$name' in $MANIFEST_PATH" }
        return matches.singleOrNull() ?: error("No torture member named '$name' in $MANIFEST_PATH")
    }

    fun checkoutDir(repoRoot: File, name: String): File = File(repoRoot, "$TORTURE_DIR/$name")

    /** The digest actually on disk, stamped by `fetch-torture.py`; null when the member is absent. */
    fun fetchedDigest(repoRoot: File, name: String): String? =
        File(checkoutDir(repoRoot, name), ".corpus-sha").takeIf { it.isFile }?.readText()?.trim()

    /** Refuses to measure a torture corpus that is absent or has drifted from its pin. */
    fun assertFetched(repoRoot: File, member: TortureMember) {
        val onDisk = fetchedDigest(repoRoot, member.name)
            ?: error("Torture corpus '${member.name}' is not fetched. Run: tooling/corpus/fetch-torture.py")
        check(onDisk == member.sha256) {
            "Torture corpus '${member.name}' does not match its pin. Re-run: tooling/corpus/fetch-torture.py"
        }
    }

    private fun parseMember(entry: JsonObject): TortureMember {
        val name = entry.get("name")?.asString?.takeIf { it.isNotBlank() }
            ?: error("A torture member is missing 'name' in $MANIFEST_PATH")
        return TortureMember(
            name = name,
            sha256 = entry.get("sha256")?.asString?.takeIf { it.isNotBlank() }
                ?: error("Torture member '$name' declares no sha256 — an unverified corpus is refused"),
            luaLevel = entry.get("luaLevel")?.asString?.takeIf { it.isNotBlank() }
                ?.let { LuaLanguageLevel.valueOf(it) }
                ?: LuaLanguageLevel.LUA54,
        )
    }
}

/**
 * What one torture sweep observed.
 *
 * Deliberately **not** [CorpusMetrics]: `CorpusGuards.assertIdentity` identity-checks `commit` and
 * `requires`, and a torture member has neither — it is pinned by digest, and its inputs contain no
 * `require` worth counting. Forcing a digest into `commit` would be a lie the ratchet then enforces.
 *
 * [parseErrors] is present because the oracle comparison is defined as
 * `lunarAccepts = (parseErrors == 0)`; dropping it would leave the judge nothing to judge against.
 */
internal data class TortureMetrics(
    val sha256: String,
    val files: Int,
    val parseErrors: Int,
    val oracleDisagreements: Int,
    /**
     * Lunar accepts and PUC does not. **Counted and baselined, not gated.**
     *
     * 364 of 1 696 on the first sweep — 21%, against one false reject. The project corpus explains
     * that direction by level-agnosticism, and for a single-level fuzz corpus that explanation does
     * not hold: the witnesses are things like a lone `9` or `\t\t\t\td`, invalid at every level.
     * What it actually measures is Lunar's deliberate parser leniency, which exists so the IDE can
     * offer something useful inside a half-typed file — a design property, and one a better
     * recovery strategy can move in either direction. Gating it would gate that design. Leaving it
     * *uncounted* was the real defect: `oracleSites` caps at 20, so the number was invisible. The
     * class is tracked as BUG-409.
     */
    val oracleFalseAccepts: Int = 0,
    val oracleSites: List<String> = emptyList(),
    /** **Gated**, for the reason [CorpusMetrics.oracleTimeouts] gives: a timeout hides a disagreement. */
    val oracleTimeouts: Int = 0,
    val lexerRoundTripFailures: Int = 0,
    val unmergedTokens: Int = 0,
    val crashes: Map<String, Int> = emptyMap(),
)

/**
 * `src/test/resources/corpus/torture-<name>.baseline`, in the same flat one-fact-per-line shape as
 * [CorpusBaseline] — a ratchet movement has to be legible in a review diff.
 */
internal object TortureBaseline {

    /** Key prefix for crash counts, mirroring [CorpusBaseline.CRASH_PREFIX]. */
    const val CRASH_PREFIX = "crash."

    /** Repeated diagnostic key for disagreement sites. */
    const val ORACLE_SITE_KEY = "oracleSite"

    fun file(repoRoot: File, name: String): File =
        File(repoRoot, "src/test/resources/corpus/torture-$name.baseline")

    fun render(metrics: TortureMetrics): String = buildString {
        appendLine("sha256=${metrics.sha256}")
        appendLine("files=${metrics.files}")
        appendLine("parseErrors=${metrics.parseErrors}")
        metrics.crashes.toSortedMap().forEach { (key, count) -> appendLine("$CRASH_PREFIX$key=$count") }
        appendLine("oracleDisagreements=${metrics.oracleDisagreements}")
        appendLine("oracleFalseAccepts=${metrics.oracleFalseAccepts}")
        appendLine("oracleTimeouts=${metrics.oracleTimeouts}")
        appendLine("lexerRoundTripFailures=${metrics.lexerRoundTripFailures}")
        appendLine("unmergedTokens=${metrics.unmergedTokens}")
        metrics.oracleSites.forEach { appendLine("$ORACLE_SITE_KEY=$it") }
    }

    fun parse(text: String): TortureMetrics {
        val rows = text.lineSequence()
            .filter { it.contains('=') && !it.startsWith("#") }
            .map { it.substringBefore('=') to it.substringAfter('=') }
            .toList()
        val scalars = rows
            .filterNot { it.first == ORACLE_SITE_KEY }
            .filterNot { it.first.startsWith(CRASH_PREFIX) }
            .toMap()
        return TortureMetrics(
            sha256 = scalars.getValue("sha256"),
            files = scalars.getValue("files").toInt(),
            parseErrors = scalars.getValue("parseErrors").toInt(),
            oracleDisagreements = scalars.getValue("oracleDisagreements").toInt(),
            oracleFalseAccepts = scalars["oracleFalseAccepts"]?.toInt() ?: 0,
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
     * `parseErrors` is **not** gated here, unlike the project corpus.
     *
     * A fuzzer corpus is mostly invalid Lua on purpose, so its parse-error count is a property of
     * the corpus rather than of Lunar, and a fix that makes the parser recover *better* can move it
     * in either direction. What is gated is the oracle's verdict on that count — a file luac accepts
     * and Lunar rejects — plus the invariants, which hold for any input at all.
     */
    fun compare(baseline: TortureMetrics, observed: TortureMetrics): CorpusComparison {
        val crashKeys = (baseline.crashes.keys + observed.crashes.keys).sorted()
        val gated = listOf(
            Triple("oracleDisagreements", baseline.oracleDisagreements, observed.oracleDisagreements),
            Triple("oracleTimeouts", baseline.oracleTimeouts, observed.oracleTimeouts),
            Triple(
                "lexerRoundTripFailures",
                baseline.lexerRoundTripFailures,
                observed.lexerRoundTripFailures,
            ),
            Triple("unmergedTokens", baseline.unmergedTokens, observed.unmergedTokens),
        ) + crashKeys.map { key ->
            Triple("$CRASH_PREFIX$key", baseline.crashes[key] ?: 0, observed.crashes[key] ?: 0)
        }
        return CorpusComparison(
            regressions = gated.filter { it.third > it.second }.map(::describe),
            improvements = gated.filter { it.third < it.second }.map(::describe),
        )
    }

    /** Identity-checks the pin and the input count, then gates the defect counters. */
    fun assertRatchet(baselineFile: File, observed: TortureMetrics) {
        check(baselineFile.isFile) {
            "No baseline at ${baselineFile.path}. Record one with -PwithCorpus -PrecordCorpusBaseline."
        }
        val baseline = parse(baselineFile.readText())
        check(baseline.sha256 == observed.sha256) {
            "Baseline was recorded against a different torture archive; re-record it."
        }
        check(baseline.files == observed.files) {
            "Torture input count changed without the pin moving — the checkout is dirty."
        }
        val comparison = compare(baseline, observed)
        comparison.improvements.forEach {
            println("[torture] IMPROVED ($it) — re-record with -PrecordCorpusBaseline")
        }
        check(comparison.regressions.isEmpty()) {
            "Torture regression:\n" + comparison.regressions.joinToString("\n")
        }
    }

    private fun describe(delta: Triple<String, Int, Int>): String =
        "${delta.first}: baseline ${delta.second} → observed ${delta.third}"
}
