package net.internetisalie.lunar.corpus

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.LuaReturnTypeMismatchInspection
import net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection
import net.internetisalie.lunar.analysis.inspections.LuaDeprecatedApiInspection
import net.internetisalie.lunar.analysis.inspections.LuaGlobalCreationInspection
import net.internetisalie.lunar.analysis.inspections.LuaLanguageLevelInspection
import net.internetisalie.lunar.analysis.inspections.LuaShadowingVariableInspection
import net.internetisalie.lunar.analysis.inspections.LuaSuspiciousConcatenationInspection
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection
import net.internetisalie.lunar.analysis.inspections.LuaUnreachableCodeInspection
import net.internetisalie.lunar.analysis.inspections.LuaUnusedLocalInspection
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryFetcher
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Path

/**
 * Sweeps pinned real-world Lua projects and ratchets the defect counts.
 *
 * Excluded from the routine `test` loop (it indexes ~300 third-party files); opt in with
 * `-PwithCorpus`, and re-record a moved baseline with `-PrecordCorpusBaseline`. Needs the corpus
 * fetched first — `tooling/corpus/fetch-corpus.py`.
 *
 * **Gate with the full `'*Corpus*'` filter, never a single member** (BUG-418). Counts are
 * byte-deterministic per invocation shape, but cross-member JVM state shifts them by a few counts —
 * deterministically — so a member-alone run reads ±2 off a baseline recorded by the full run and
 * can fail as a phantom regression. Member-alone runs are for diagnosis; the ratchet's contract is
 * that recording and verification share the invocation shape.
 */
@RunWith(JUnit4::class)
class LuaCorpusSweepTest : BasePlatformTestCase() {
    private var provisioned: List<Path> = emptyList()
    private var savedSourcePath: String = ""

    override fun getTestDataPath(): String = System.getProperty("user.dir")

    override fun setUp() {
        super.setUp()
        CorpusDefinitionLibraries.requireSettingsSubscriber(project)
        // The corpus lives in the out-of-repo test/ tree, reached through a symlink whose CANONICAL
        // path the sweep uses (applyModuleRoot resolves it for sourcePath). On the builder that
        // canonical path happens to sit under ${'$'}HOME, which VfsRootAccess allows by default; in the
        // CI pod it is /cache/lunar/test — outside every default root — and the platform's test
        // guard throws VfsRootAccessNotAllowedError from require resolution instead (measured:
        // 23 penlight parse-crashes, requires 100 → 43, identity refusal — CI run 9735). Sanction
        // it explicitly so the suite is location-independent rather than home-directory-lucky.
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(
            testRootDisposable,
            File(System.getProperty("user.dir"), CorpusManifest.CORPUS_DIR).canonicalPath,
        )
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(
            testRootDisposable,
            LuaDefinitionLibraryFetcher.defaultCacheRoot().toString(),
        )
        // The light project is shared across classes, so an earlier class's enable list must not
        // reach this member's sweep.
        LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf()
        // The ten language-only inspections of design §3.3. Redis/luacheck/JSON-schema tools are
        // excluded: they would measure the environment (absent binary, absent Redis, absent schema
        // mapping) rather than the plugin.
        myFixture.enableInspections(
            LuaUndeclaredVariableInspection(),
            LuaGlobalCreationInspection(),
            LuaUnusedLocalInspection(),
            LuaShadowingVariableInspection(),
            LuaDeprecatedApiInspection(),
            LuaSuspiciousConcatenationInspection(),
            LuaUnreachableCodeInspection(),
            LuaLanguageLevelInspection(),
            LuaTypeAssignabilityInspection(),
            LuaReturnTypeMismatchInspection(),
        )
        savedSourcePath = CorpusSourcePath.snapshot(project)
    }

    /**
     * The member's `moduleRoot` must not reach the next member, and neither may its libraries
     * (MAINT-37-09, design §3.10): restore first, so `release`'s broadcast sees the restored path;
     * refresh the roots directly only when `release` broadcast nothing.
     */
    override fun tearDown() {
        try {
            val moduleRootRestored = CorpusSourcePath.restore(project, savedSourcePath)
            CorpusDefinitionLibraries.release(project, provisioned)
            if (moduleRootRestored && provisioned.isEmpty()) CorpusDefinitionLibraries.refreshRoots(project)
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testLuacheckCorpus() = sweepAndRatchet("luacheck")

    @Test
    fun testLuarocksCorpus() = sweepAndRatchet("luarocks")

    @Test
    fun testZerobraneCorpus() = sweepAndRatchet("zerobrane")

    /**
     * Penlight carries the LDoc constructs BUG-393 was found through, at an eighth of KOReader's
     * sweep cost (57 indexed files/~73 s against 477/609 s) — which is why KOReader was measured and
     * parked rather than admitted (MAINT-33 risks-and-gaps).
     */
    @Test
    fun testPenlightCorpus() = sweepAndRatchet("penlight")

    private fun sweepAndRatchet(name: String) {
        val repoRoot = File(testDataPath)
        val entry = CorpusManifest.entry(repoRoot, name)
        CorpusGuards.assertCorpusFetched(repoRoot, entry)
        // Pinned per project: the level selects which stdlib globals are known, so leaving it at
        // the LUA54 default would make every inspection count a function of that default.
        LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = entry.luaLevel

        val attribution = attributionMode()
        // Deleted up front, so an arm that fails before writing leaves no older run's tally behind.
        if (attribution != null) CorpusAttribution.tallyFile(repoRoot, attribution, name).delete()
        val refresh = refreshFor(attribution, entry, repoRoot)
        val observed = measure(name, entry, repoRoot)
        if (attribution != null) {
            CorpusAttribution.checkArm(attribution, observed)
            CorpusAttribution.write(CorpusAttribution.tallyFile(repoRoot, attribution, name), observed, refresh)
            return
        }

        val baselineFile = CorpusBaseline.file(repoRoot, name)
        if (System.getProperty("lunar.corpus.record") == "true") {
            recordBaseline(baselineFile, observed)
        } else {
            CorpusGuards.assertRatchet(baselineFile, observed)
        }
    }

    /**
     * Provisions the member's pins (or, in the bare arm, gives it the pinned arm's broadcast without
     * a library) inside the refresh observation window, and returns what the window saw (MAINT-37
     * design §2.7). Read before the sweep, so the sweep's own PSI events stay outside it.
     */
    private fun refreshFor(
        attribution: CorpusAttributionMode?,
        entry: CorpusEntry,
        repoRoot: File,
    ): RefreshObservation {
        val bare = attribution == CorpusAttributionMode.BARE
        val staged = if (bare) emptyList() else CorpusDefinitionLibraries.stage(project, entry, repoRoot)
        provisioned = staged
        val observed =
            CorpusDefinitionLibraries.observeRefresh(project, testRootDisposable) {
                if (bare) {
                    CorpusDefinitionLibraries.refreshUnpinned(project)
                    emptyList()
                } else {
                    CorpusDefinitionLibraries.enable(project, entry, repoRoot, staged)
                }
            }
        provisioned = observed.cacheDirs
        return observed.observation
    }

    private fun measure(
        name: String,
        entry: CorpusEntry,
        repoRoot: File,
    ): CorpusMetrics {
        val startedAt = System.nanoTime()
        val observed = CorpusSweep.run(myFixture, entry, repoRoot)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        report(name, observed, elapsedMs)
        return observed
    }

    /** The mutual-exclusion rule with `-PrecordCorpusBaseline` lives in `forRun` (design §3.8). */
    private fun attributionMode(): CorpusAttributionMode? =
        CorpusAttributionMode.forRun(
            System.getProperty(CorpusAttributionMode.PROPERTY),
            System.getProperty("lunar.corpus.record"),
        )

    /** Advisory output (MAINT-33-09). `elapsedMs` is printed only — never baselined, never gated. */
    private fun report(
        name: String,
        observed: CorpusMetrics,
        elapsedMs: Long,
    ) {
        println(
            "[corpus:$name] files=${observed.files} parseErrors=${observed.parseErrors} " +
                "requires=${observed.requires} unresolvedRequires=${observed.unresolvedRequires} " +
                "elapsedMs=$elapsedMs",
        )
        // The oracle's own liveness, printed because it is otherwise invisible: `oracleDisagreements
        // = 0` reads identically whether the judge found nothing or judged nothing. `assertDiscriminates`
        // is what *enforces* it; these lines are what let a reader confirm it from the log.
        println(
            "[corpus:$name] oracleDisagreements=${observed.oracleDisagreements} " +
                "oracleTimeouts=${observed.oracleTimeouts} " +
                "lexerRoundTripFailures=${observed.lexerRoundTripFailures} " +
                "unmergedTokens=${observed.unmergedTokens}",
        )
        observed.oracleSites.forEach { println("[corpus:$name] oracle site $it") }
        observed.crashes.toSortedMap().forEach { (key, count) -> println("[corpus:$name] crash $key=$count") }
        observed.inspectionHits.toSortedMap().forEach { (id, count) ->
            println("[corpus:$name] inspection $id=$count")
        }
        observed.ballast.toSortedMap().filterValues { it.unclaimed > 0 }.forEach { (key, group) ->
            println("[corpus:$name] unclaimed ballast $key=${group.unclaimed}")
        }
        observed.parseErrorFiles.forEach { println("[corpus:$name] parse errors in $it") }
    }

    private fun recordBaseline(
        baselineFile: File,
        observed: CorpusMetrics,
    ) {
        baselineFile.parentFile.mkdirs()
        val rendered = CorpusBaseline.render(observed)
        baselineFile.writeText(rendered)
        // Echoed as well as written: the suite runs on the remote builder, so the console is the
        // reliable way to get a freshly recorded baseline back into the working tree.
        println("[corpus] recorded ${baselineFile.path}:\n$rendered")
    }
}
