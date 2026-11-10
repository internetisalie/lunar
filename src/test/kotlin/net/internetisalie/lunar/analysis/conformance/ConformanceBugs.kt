package net.internetisalie.lunar.analysis.conformance

import com.google.gson.JsonParser
import java.io.File

/** `bugs.json` (MAINT-55 design §2.7) and its check against a run's results (§3.7). */
object ConformanceBugs {
    private val DISAGREEING =
        setOf(ConformanceOutcome.MISSED, ConformanceOutcome.FALSE_REPORT, ConformanceOutcome.MISPLACED).map { it.json }

    fun load(file: File): Map<String, List<String>> {
        val bugs = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject.getAsJsonObject("bugs")
        return bugs.entrySet().associate { (bug, cases) -> bug to cases.asJsonArray.map { it.asString } }.toSortedMap()
    }

    fun problems(
        results: ConformanceResults,
        bugs: Map<String, List<String>>,
    ): List<String> {
        val misfiled =
            bugs.toSortedMap().flatMap { (bug, cases) ->
                cases.mapNotNull { case ->
                    val outcome = results.cases[case]?.outcome
                    "$case is filed under $bug but is ${outcome ?: "absent"}".takeIf { outcome !in DISAGREEING }
                }
            }
        val filed = bugs.values.flatten()
        val twice =
            filed
                .groupBy { it }
                .filterValues { it.size > 1 }
                .keys
                .sorted()
                .map { "$it is filed twice" }
        val unfiled =
            results.cases
                .filter { (id, result) -> result.outcome in DISAGREEING && id !in filed }
                .keys
                .map { "unfiled $it" }
        return misfiled + twice + unfiled
    }
}
