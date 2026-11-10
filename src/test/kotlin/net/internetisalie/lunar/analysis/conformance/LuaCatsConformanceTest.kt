package net.internetisalie.lunar.analysis.conformance

import com.google.gson.JsonParser
import com.intellij.openapi.progress.ProgressManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.corpus.CorpusTarget
import net.internetisalie.lunar.corpus.SavedTarget
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.util.TreeMap

/**
 * MAINT-55 design §3.4: runs every LuaCATS conformance case alone at Lua 5.4, scores it against its
 * markers and recorded deviations, writes `build/conformance/results.json`, and ratchets against the
 * committed `results.json`. Record a new baseline with `-PrecordConformance` and copy
 * `build/conformance/recorded/results.json` back by hand.
 */
@RunWith(JUnit4::class)
class LuaCatsConformanceTest : BasePlatformTestCase() {
    private var saved: SavedTarget? = null

    override fun setUp() {
        super.setUp()
        saved = ConformanceRunner.pin(project)
    }

    override fun tearDown() {
        try {
            saved?.let { CorpusTarget.restore(project, it) }
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testConformanceRatchet() {
        val deviations = ConformanceDeviations.load(ConformancePaths.deviations())
        val scored = scoreAll(deviations)
        val deviationProblems = ConformanceDeviations.check(deviations, scored.mapValues { it.value.rule })
        if (deviationProblems.isNotEmpty()) fail(deviationProblems.joinToString("\n"))
        val results =
            ConformanceResults(
                ConformanceResults.SCHEMA,
                lualsCommit(),
                LEVEL,
                scored,
                ConformanceResultsJson.tally(scored),
            )
        write(ConformancePaths.actual(), results)
        if (System.getProperty(RECORD_PROPERTY) == "true") {
            write(ConformancePaths.recorded(), results)
            return
        }
        assertRatchet(results)
    }

    private fun scoreAll(deviations: Map<String, ConformanceDeviation>): TreeMap<String, CaseResult> {
        val scored = TreeMap<String, CaseResult>()
        ConformanceCaseParser.loadAll(ConformancePaths.cases()).forEach { scored[it.id] = score(it, deviations) }
        return scored
    }

    private fun score(
        case: ConformanceCase,
        deviations: Map<String, ConformanceDeviation>,
    ): CaseResult {
        ProgressManager.checkCanceled()
        val findings = ConformanceRunner.findings(myFixture, case)
        val rule = if (case.rule == ConformanceRules.ARG) ConformanceRules.argRule(myFixture.file) else case.rule
        val observed = ConformanceScorer.observed(case, findings)
        val outcome = ConformanceScorer.outcome(observed, deviations[case.id])
        val rendered = findings.map { it.render() }.distinct().sorted()
        return CaseResult(rule, observed.json, outcome.json, case.must.sorted(), case.may.sorted(), rendered)
    }

    private fun assertRatchet(actual: ConformanceResults) {
        val baselineFile = ConformancePaths.results()
        if (!baselineFile.isFile) fail("no baseline; record with -PrecordConformance")
        val baseline = ConformanceResultsJson.parse(baselineFile.readText(Charsets.UTF_8))
        ConformanceRatchet.notes(baseline, actual).forEach { println("[conformance] $it") }
        val violations = ConformanceRatchet.violations(baseline, actual)
        if (violations.isNotEmpty()) fail(violations.joinToString("\n"))
    }

    private fun lualsCommit(): String =
        JsonParser
            .parseString(ConformancePaths.importRecord().readText(Charsets.UTF_8))
            .asJsonObject
            .getAsJsonObject("luals")
            .get("commit")
            .asString

    private fun write(
        target: File,
        results: ConformanceResults,
    ) {
        target.parentFile.mkdirs()
        target.writeText(ConformanceResultsJson.render(results), Charsets.UTF_8)
    }

    private companion object {
        const val LEVEL = "5.4"
        const val RECORD_PROPERTY = "lunar.conformance.record"
    }
}
