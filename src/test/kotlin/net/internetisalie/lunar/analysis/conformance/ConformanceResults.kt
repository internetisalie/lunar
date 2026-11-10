package net.internetisalie.lunar.analysis.conformance

import com.google.gson.GsonBuilder
import java.util.TreeMap

/** One case's row of `results.json` (MAINT-55 design §2.5). */
data class CaseResult(
    val rule: String,
    val observed: String,
    val outcome: String,
    val must: List<Int>,
    val may: List<Int>,
    val findings: List<String>,
)

/** `results.json` (MAINT-55 design §2.5); sorted maps, so a re-render is byte-identical. */
data class ConformanceResults(
    val schema: Int,
    val luals: String,
    val level: String,
    val cases: TreeMap<String, CaseResult>,
    val rules: TreeMap<String, TreeMap<String, Int>>,
) {
    companion object {
        const val SCHEMA = 1
    }
}

object ConformanceResultsJson {
    private val GSON = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    fun render(results: ConformanceResults): String = GSON.toJson(results) + "\n"

    fun parse(text: String): ConformanceResults = GSON.fromJson(text, ConformanceResults::class.java)

    /** Per rule, the count of each `outcome`; only non-zero entries appear. */
    fun tally(cases: Map<String, CaseResult>): TreeMap<String, TreeMap<String, Int>> {
        val rules = TreeMap<String, TreeMap<String, Int>>()
        cases.values.forEach { result ->
            val counts = rules.getOrPut(result.rule) { TreeMap() }
            counts[result.outcome] = (counts[result.outcome] ?: 0) + 1
        }
        return rules
    }
}
