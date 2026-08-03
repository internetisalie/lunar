package net.internetisalie.lunar.corpus

import net.internetisalie.lunar.lang.LuaLanguageLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * Proves the corpus gate can actually fail. A ratchet that cannot go red is worthless, and a green
 * corpus run means nothing without these.
 *
 * Deliberately **not** named `*Corpus*`: nothing here needs a platform fixture or a fetched
 * corpus, so it must run in the routine suite rather than behind `-PwithCorpus`.
 * `excludeTestsMatching("*Corpus*")` is case-sensitive and does not match the lowercase
 * `…lunar.corpus.` package segment.
 */
@RunWith(JUnit4::class)
class BaselineRatchetTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun metrics(
        parseErrors: Int = 3,
        unresolvedRequires: Int = 3,
        inspectionHits: Map<String, Int> = mapOf("LuaUndeclaredVariable" to 7),
    ) = CorpusMetrics(
        commit = "cc089e3f",
        files = 132,
        parseErrors = parseErrors,
        requires = 3,
        unresolvedRequires = unresolvedRequires,
        parseErrorFiles = listOf("spec/samples/utf8_error.lua"),
        inspectionHits = inspectionHits,
    )

    @Test
    fun renderParseRoundTrip() {
        val original = metrics(
            inspectionHits = mapOf(
                "LuaUndeclaredVariable" to 41,
                "LuaTypeAssignability" to 2,
                CorpusMetrics.UNATTRIBUTED to 0,
            ),
        ).copy(
            symbolHits = mapOf("LuaUndeclaredVariable.wx" to 812, "LuaUndeclaredVariable.ide.config" to 9),
            ballast = mapOf(
                "tl" to BallastGroup(117, claimed = false),
                "rockspec" to BallastGroup(53, claimed = true),
                ".luacov" to BallastGroup(1, claimed = false),
            ),
        )
        assertEquals(original, CorpusBaseline.parse(CorpusBaseline.render(original)))
    }

    /**
     * The group key may contain or begin with a dot, so the inverse parse must be positional —
     * `ballast.unclaimed..luacov` is flag `unclaimed`, key `.luacov`, and a naive `split('.')`
     * mangles it.
     */
    @Test
    fun ballastKeyInverseParse() {
        val rendered = CorpusBaseline.render(
            metrics().copy(
                ballast = mapOf(
                    ".luacov" to BallastGroup(1, claimed = false),
                    "config.ld" to BallastGroup(2, claimed = true),
                ),
            ),
        )
        assertTrue(
            "Expected a doubled dot for the dotfile key, got:\n$rendered",
            rendered.contains("ballast.unclaimed..luacov=1"),
        )
        val parsed = CorpusBaseline.parse(rendered).ballast
        assertEquals(BallastGroup(1, claimed = false), parsed[".luacov"])
        assertEquals(BallastGroup(2, claimed = true), parsed["config.ld"])
    }

    /**
     * The symbol breakdown is diagnostic, not a defect count. It must round-trip (including a
     * symbol containing dots, e.g. `ide.config`) but never move the gate.
     */
    @Test
    fun symbolBreakdownIsReportedNeverGated() {
        val comparison = CorpusBaseline.compare(
            metrics().copy(symbolHits = emptyMap()),
            metrics().copy(symbolHits = mapOf("LuaUndeclaredVariable.wx" to 812)),
        )
        assertTrue(comparison.regressions.isEmpty())
        assertTrue(comparison.improvements.isEmpty())
    }

    @Test
    fun symbolKeyWithDotsRoundTrips() {
        val original = metrics().copy(symbolHits = mapOf("LuaUndeclaredVariable.ide.config" to 9))
        assertEquals(original.symbolHits, CorpusBaseline.parse(CorpusBaseline.render(original)).symbolHits)
    }

    /** Ballast is a discovery signal, not a defect count — a new unclaimed group must not fail. */
    @Test
    fun ballastIsReportedNeverGated() {
        val comparison = CorpusBaseline.compare(
            metrics().copy(ballast = emptyMap()),
            metrics().copy(ballast = mapOf("tl" to BallastGroup(117, claimed = false))),
        )
        assertTrue(comparison.regressions.isEmpty())
        assertTrue(comparison.improvements.isEmpty())
    }

    @Test
    fun gatedMetricIncreaseIsRegression() {
        val comparison = CorpusBaseline.compare(metrics(parseErrors = 3), metrics(parseErrors = 4))
        assertEquals(listOf("parseErrors: baseline 3 → observed 4"), comparison.regressions)
        assertTrue(comparison.improvements.isEmpty())
    }

    @Test
    fun gatedMetricDecreaseIsImprovement() {
        val comparison = CorpusBaseline.compare(metrics(parseErrors = 3), metrics(parseErrors = 2))
        assertTrue("A decrease must not fail the build", comparison.regressions.isEmpty())
        assertEquals(listOf("parseErrors: baseline 3 → observed 2"), comparison.improvements)
    }

    @Test
    fun inspectionHitIncreaseIsRegression() {
        val comparison = CorpusBaseline.compare(
            metrics(inspectionHits = mapOf("LuaUndeclaredVariable" to 7)),
            metrics(inspectionHits = mapOf("LuaUndeclaredVariable" to 8)),
        )
        assertEquals(
            listOf("inspection.LuaUndeclaredVariable: baseline 7 → observed 8"),
            comparison.regressions,
        )
    }

    /**
     * BUG-390 makes the counts non-reproducible, so while any file fails to highlight the
     * per-inspection keys must be advisory — a flaky gate is worse than no gate.
     */
    @Test
    fun inspectionHitsAreAdvisoryWhileHighlightsFail() {
        val withFailures = { n: Int ->
            metrics(inspectionHits = mapOf("LuaUndeclaredVariable" to n, CorpusMetrics.HIGHLIGHT_FAILURES to 42))
        }
        val comparison = CorpusBaseline.compare(withFailures(7), withFailures(8))
        assertTrue(
            "Inspection counts must not gate while highlights fail; got ${comparison.regressions}",
            comparison.regressions.isEmpty(),
        )
    }

    /** …but the failure count itself is always gated: it is the thing that has to come down. */
    @Test
    fun highlightFailureIncreaseIsAlwaysRegression() {
        val comparison = CorpusBaseline.compare(
            metrics(inspectionHits = mapOf(CorpusMetrics.HIGHLIGHT_FAILURES to 42)),
            metrics(inspectionHits = mapOf(CorpusMetrics.HIGHLIGHT_FAILURES to 43)),
        )
        assertEquals(
            listOf("inspection.highlightFailures: baseline 42 → observed 43"),
            comparison.regressions,
        )
    }

    @Test
    fun newlyFiringInspectionIsRegression() {
        val comparison = CorpusBaseline.compare(
            metrics(inspectionHits = emptyMap()),
            metrics(inspectionHits = mapOf("LuaUnusedLocal" to 4)),
        )
        assertEquals(listOf("inspection.LuaUnusedLocal: baseline 0 → observed 4"), comparison.regressions)
    }

    @Test
    fun missingScalarKeyThrows() {
        val truncated = CorpusBaseline.render(metrics()).lineSequence()
            .filterNot { it.startsWith("unresolvedRequires=") }
            .joinToString("\n")
        try {
            CorpusBaseline.parse(truncated)
            fail("An unreadable baseline must fail loudly, not read as 'no regression'")
        } catch (expected: NoSuchElementException) {
            assertTrue(expected.message.orEmpty().contains("unresolvedRequires"))
        }
    }

    @Test
    fun malformedManifestRowThrows() {
        val repoRoot = manifestRoot("short\thttps://example.invalid/x.git\tdeadbeef")
        val failure = runCatching { CorpusManifest.load(repoRoot) }.exceptionOrNull()
        assertTrue("Expected a malformed-row failure, got $failure", failure is IllegalArgumentException)
    }

    @Test
    fun duplicateManifestNameThrows() {
        val row = "dup\thttps://example.invalid/x.git\tdeadbeef\tsrc"
        val failure = runCatching { CorpusManifest.entry(manifestRoot(row, row), "dup") }.exceptionOrNull()
        assertTrue(
            "A duplicate must not be reported as absent; got: ${failure?.message}",
            failure?.message.orEmpty().contains("Duplicate"),
        )
    }

    @Test
    fun manifestLuaLevelDefaultsAndParses() {
        val repoRoot = manifestRoot(
            "plain\thttps://example.invalid/x.git\tdeadbeef\tsrc",
            "pinned\thttps://example.invalid/y.git\tcafebabe\tsrc\t\tLUA51",
        )
        assertEquals(LuaLanguageLevel.LUA54, CorpusManifest.entry(repoRoot, "plain").luaLevel)
        assertEquals(LuaLanguageLevel.LUA51, CorpusManifest.entry(repoRoot, "pinned").luaLevel)
    }

    /** TC 9 — the guard refuses an absent corpus instead of measuring nothing. */
    @Test
    fun absentCorpusFailsWithFetchInstruction() {
        val repoRoot = manifestRoot("ghost\thttps://example.invalid/x.git\tdeadbeef\tsrc")
        val entry = CorpusManifest.entry(repoRoot, "ghost")
        val failure = runCatching { CorpusGuards.assertCorpusFetched(repoRoot, entry) }.exceptionOrNull()
        assertTrue(
            "Expected the fetch instruction, got: ${failure?.message}",
            failure?.message.orEmpty().contains("tooling/corpus/fetch-corpus.sh"),
        )
    }

    /**
     * TC 8 — the checkout is at its pin; only the recorded baseline is stale. Re-pinning the
     * manifest instead would trip [CorpusGuards.assertCorpusFetched] first, with a different
     * message, so this exercises the identity check rather than the fetch guard.
     */
    @Test
    fun divergentBaselineCommitFailsWithReRecordInstruction() {
        val baseline = temp.newFile("stale.baseline")
        baseline.writeText(CorpusBaseline.render(metrics()))
        val observed = metrics().copy(commit = "990ec6ca")
        val failure = runCatching { CorpusGuards.assertRatchet(baseline, observed) }.exceptionOrNull()
        assertTrue(
            "Expected the re-record instruction, got: ${failure?.message}",
            failure?.message.orEmpty().contains("different corpus commit"),
        )
    }

    @Test
    fun missingBaselineIsNeverTreatedAsPassing() {
        val absent = File(temp.root, "not-recorded.baseline")
        val failure = runCatching { CorpusGuards.assertRatchet(absent, metrics()) }.exceptionOrNull()
        assertTrue(
            "Expected the record instruction, got: ${failure?.message}",
            failure?.message.orEmpty().contains("-PrecordCorpusBaseline"),
        )
    }

    /** A synthetic repo root holding only `tooling/corpus/corpus.tsv` — no checkout, no fixture. */
    private fun manifestRoot(vararg rows: String): File {
        val repoRoot = temp.newFolder()
        val manifest = File(repoRoot, "tooling/corpus/corpus.tsv")
        manifest.parentFile.mkdirs()
        manifest.writeText(rows.joinToString("\n", postfix = "\n"))
        return repoRoot
    }
}
