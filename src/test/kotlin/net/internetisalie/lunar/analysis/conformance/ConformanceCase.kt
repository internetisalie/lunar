package net.internetisalie.lunar.analysis.conformance

/**
 * MAINT-55 design §2.1: one conformance case. [must] and [may] are 1-based lines of [body]; every
 * other line must stay silent.
 */
data class ConformanceCase(
    val id: String,
    val rule: String,
    val source: String,
    val body: List<String>,
    val must: Set<Int>,
    val may: Set<Int>,
) {
    /** The text given to the fixture: the body lines joined by `\n`, plus a final `\n`. */
    val text: String
        get() = body.joinToString("\n") + "\n"
}

/** One snapshot error as the 1-based document lines its element spans (design §3.2). */
data class ConformanceFinding(
    val startLine: Int,
    val endLine: Int,
    val message: String,
) {
    fun render(): String = "$startLine-$endLine:$message"

    fun covers(line: Int): Boolean = line in startLine..endLine
}

/** Design §2.5's outcomes, by their JSON names. */
enum class ConformanceOutcome(
    val json: String,
) {
    AGREE("agree"),
    MISSED("missed"),
    FALSE_REPORT("false-report"),
    MISPLACED("misplaced"),
    DEVIATION("deviation"),
    DEVIATION_BROKEN("deviation-broken"),
    ;

    companion object {
        fun of(json: String): ConformanceOutcome =
            entries.firstOrNull { it.json == json } ?: throw IllegalArgumentException("unknown outcome $json")
    }
}
