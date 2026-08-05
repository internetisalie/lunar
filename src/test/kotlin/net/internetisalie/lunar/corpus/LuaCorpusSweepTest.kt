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
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * Sweeps pinned real-world Lua projects and ratchets the defect counts.
 *
 * Excluded from the routine `test` loop (it indexes ~300 third-party files); opt in with
 * `-PwithCorpus`, and re-record a moved baseline with `-PrecordCorpusBaseline`. Needs the corpus
 * fetched first — `tooling/corpus/fetch-corpus.sh`.
 */
@RunWith(JUnit4::class)
class LuaCorpusSweepTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = System.getProperty("user.dir")

    override fun setUp() {
        super.setUp()
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

        val startedAt = System.nanoTime()
        val observed = CorpusSweep.run(myFixture, entry, CorpusManifest.checkoutDir(repoRoot, name))
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        report(name, observed, elapsedMs)

        val baselineFile = CorpusBaseline.file(repoRoot, name)
        if (System.getProperty("lunar.corpus.record") == "true") {
            recordBaseline(baselineFile, observed)
        } else {
            CorpusGuards.assertRatchet(baselineFile, observed)
        }
    }

    /** Advisory output (MAINT-33-09). `elapsedMs` is printed only — never baselined, never gated. */
    private fun report(name: String, observed: CorpusMetrics, elapsedMs: Long) {
        println(
            "[corpus:$name] files=${observed.files} parseErrors=${observed.parseErrors} " +
                "requires=${observed.requires} unresolvedRequires=${observed.unresolvedRequires} " +
                "elapsedMs=$elapsedMs",
        )
        observed.inspectionHits.toSortedMap().forEach { (id, count) ->
            println("[corpus:$name] inspection $id=$count")
        }
        observed.ballast.toSortedMap().filterValues { it.unclaimed > 0 }.forEach { (key, group) ->
            println("[corpus:$name] unclaimed ballast $key=${group.unclaimed}")
        }
        observed.parseErrorFiles.forEach { println("[corpus:$name] parse errors in $it") }
    }

    private fun recordBaseline(baselineFile: File, observed: CorpusMetrics) {
        baselineFile.parentFile.mkdirs()
        val rendered = CorpusBaseline.render(observed)
        baselineFile.writeText(rendered)
        // Echoed as well as written: the suite runs on the remote builder, so the console is the
        // reliable way to get a freshly recorded baseline back into the working tree.
        println("[corpus] recorded ${baselineFile.path}:\n$rendered")
    }
}
