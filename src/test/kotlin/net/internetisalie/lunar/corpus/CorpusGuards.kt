package net.internetisalie.lunar.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.File

/**
 * The two guards that must fire *before* any metric is compared (design §2.4a).
 *
 * Extracted from [LuaCorpusSweepTest] so that [BaselineRatchetTest] can exercise them without a
 * platform fixture: neither touches `myFixture`, PSI or VFS — [assertCorpusFetched] reads only the
 * on-disk pin stamp, [assertRatchet] only the baseline file and the observed metrics.
 */
internal object CorpusGuards {

    /** Refuses to measure a corpus that is absent or has drifted from its manifest pin. */
    internal fun assertCorpusFetched(repoRoot: File, entry: CorpusEntry) {
        val onDisk = CorpusManifest.checkedOutCommit(repoRoot, entry.name)
        assertNotNull(
            "Corpus '${entry.name}' is not fetched. Run: tooling/corpus/fetch-corpus.sh",
            onDisk,
        )
        assertEquals(
            "Corpus '${entry.name}' is at the wrong commit. Re-run: tooling/corpus/fetch-corpus.sh",
            entry.commit,
            onDisk,
        )
    }

    /**
     * The ratchet. Identity-checks `commit`, `files` and `requires` (design §3.2 step 1) before
     * delegating the gated comparison to [CorpusBaseline.compare].
     */
    internal fun assertRatchet(baselineFile: File, observed: CorpusMetrics) {
        assertTrue(
            "No baseline at ${baselineFile.path}. Record one with -PwithCorpus -PrecordCorpusBaseline.",
            baselineFile.isFile,
        )
        val baseline = CorpusBaseline.parse(baselineFile.readText())
        assertIdentity(baseline, observed)

        val comparison = CorpusBaseline.compare(baseline, observed)
        comparison.improvements.forEach {
            println("[corpus] IMPROVED ($it) — re-record with -PrecordCorpusBaseline")
        }
        assertTrue(
            "Corpus regression:\n" + comparison.regressions.joinToString("\n"),
            comparison.regressions.isEmpty(),
        )
    }

    private fun assertIdentity(baseline: CorpusMetrics, observed: CorpusMetrics) {
        assertEquals(
            "Baseline was recorded against a different corpus commit; re-record it.",
            baseline.commit,
            observed.commit,
        )
        assertEquals(
            "Corpus file count changed without the pin moving — the checkout is dirty.",
            baseline.files,
            observed.files,
        )
        // Identity-checked, not gated: recognised-require coverage may legitimately RISE when a
        // resolution bug is fixed (BUG-389 takes luacheck from 3 to ~155). An unresolvedRequires
        // floor computed against the old coverage is not comparable to the new one, so force a
        // deliberate re-record rather than reporting a misleading regression.
        assertEquals(
            "Recognised require count changed; the unresolvedRequires floor is no longer " +
                "comparable. Re-record with -PrecordCorpusBaseline.",
            baseline.requires,
            observed.requires,
        )
    }
}
