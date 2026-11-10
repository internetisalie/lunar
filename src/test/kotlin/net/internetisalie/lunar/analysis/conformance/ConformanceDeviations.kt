package net.internetisalie.lunar.analysis.conformance

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/** One recorded ruling (MAINT-55 design §2.4): Lunar disagrees with LuaLS on [case] on purpose. */
data class ConformanceDeviation(
    val case: String,
    val rule: String,
    val policy: String,
    val expect: ConformanceOutcome,
    val reason: String,
    val ref: String,
    val decidedBy: String,
    val decidedOn: String,
)

/** Loads and checks `deviations.json` (MAINT-55 design §2.4). */
object ConformanceDeviations {
    private val FIELDS = listOf("case", "rule", "policy", "expect", "reason", "ref", "decidedBy", "decidedOn")
    private val EXPECTED =
        setOf(ConformanceOutcome.MISSED, ConformanceOutcome.FALSE_REPORT, ConformanceOutcome.MISPLACED)

    /** The deviations by case id, in id order; a blank field, a bad `expect` or a repeated case is an error. */
    fun load(file: File): Map<String, ConformanceDeviation> {
        val root = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
        val entries = root.getAsJsonArray("deviations").map { entry(it.asJsonObject) }
        val repeated =
            entries
                .groupBy { it.case }
                .filterValues { it.size > 1 }
                .keys
                .sorted()
        require(repeated.isEmpty()) { "${file.name}: case listed twice: ${repeated.joinToString(", ")}" }
        return entries.associateBy { it.case }.toSortedMap()
    }

    /** One line per deviation whose case is unknown or resolves to another rule, in deviation-id order. */
    fun check(
        deviations: Map<String, ConformanceDeviation>,
        resolved: Map<String, String>,
    ): List<String> =
        deviations.keys.sorted().mapNotNull { id ->
            val deviation = deviations.getValue(id)
            val resolvedRule = resolved[id]
            when {
                resolvedRule == null -> "deviation $id is for an unknown case"
                resolvedRule != deviation.rule ->
                    "deviation $id has rule ${deviation.rule} but the case resolves to $resolvedRule"
                else -> null
            }
        }

    private fun entry(json: JsonObject): ConformanceDeviation {
        val values = FIELDS.associateWith { field(json, it) }
        val expect = ConformanceOutcome.entries.firstOrNull { it.json == values.getValue("expect") }
        require(expect != null && expect in EXPECTED) {
            "deviation ${values.getValue("case")}: expect must be missed, false-report or misplaced"
        }
        return ConformanceDeviation(
            case = values.getValue("case"),
            rule = values.getValue("rule"),
            policy = values.getValue("policy"),
            expect = expect,
            reason = values.getValue("reason"),
            ref = values.getValue("ref"),
            decidedBy = values.getValue("decidedBy"),
            decidedOn = values.getValue("decidedOn"),
        )
    }

    private fun field(
        json: JsonObject,
        name: String,
    ): String {
        val element = json.get(name)
        val value = if (element != null && element.isJsonPrimitive) element.asString else ""
        require(value.isNotBlank()) { "deviation entry ${json.get("case")}: field $name is missing or blank" }
        return value
    }
}
