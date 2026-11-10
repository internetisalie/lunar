package net.internetisalie.lunar.analysis.conformance

import java.io.File

/** MAINT-55 design §2.1: reads case files into [ConformanceCase]s. */
object ConformanceCaseParser {
    private const val HEADER_PREFIX = "-- ==== case"
    private val HEADER = Regex("""^-- ==== case ([a-z0-9-]+-[0-9]{3}) rule=([a-zA-Z.]+) source=(\S+)$""")
    private val MARKER = Regex("""\s--\s(E\??)$""")

    private data class Header(
        val id: String,
        val rule: String,
        val source: String,
    )

    private data class Location(
        val fileName: String,
        val lineNumber: Int,
    ) {
        override fun toString(): String = "$fileName:$lineNumber"
    }

    fun parse(
        text: String,
        fileName: String,
    ): List<ConformanceCase> {
        val cases = mutableListOf<ConformanceCase>()
        var header: Header? = null
        val body = mutableListOf<String>()
        text.lines().forEachIndexed { index, line ->
            if (!line.startsWith(HEADER_PREFIX)) {
                if (header != null) body += line
                return@forEachIndexed
            }
            header?.let { cases += build(it, body) }
            body.clear()
            header = header(line, Location(fileName, index + 1))
        }
        header?.let { cases += build(it, body) }
        return cases
    }

    /** Every `*.lua` case file under [dir], by file name; an id occurring twice is an error. */
    fun loadAll(dir: File): List<ConformanceCase> {
        val files = dir.listFiles { file -> file.isFile && file.extension == "lua" }.orEmpty().sortedBy { it.name }
        val cases = files.flatMap { parse(it.readText(Charsets.UTF_8), it.name) }
        val duplicates =
            cases
                .groupBy { it.id }
                .filterValues { it.size > 1 }
                .keys
                .sorted()
        require(duplicates.isEmpty()) { "duplicate case ids: ${duplicates.joinToString(", ")}" }
        return cases
    }

    private fun header(
        line: String,
        location: Location,
    ): Header {
        val match = HEADER.matchEntire(line) ?: throw IllegalArgumentException("$location: malformed case header")
        val (id, rule, source) = match.destructured
        require(rule == ConformanceRules.ARG || rule in ConformanceRules.RULES) { "$location: unknown rule $rule" }
        return Header(id, rule, source)
    }

    private fun build(
        header: Header,
        rawBody: List<String>,
    ): ConformanceCase {
        val body = rawBody.dropLastWhile { it.isEmpty() }
        val markers =
            body.mapIndexedNotNull { index, line ->
                MARKER.find(line.trimEnd())?.let {
                    index + 1 to
                        it.groupValues[1]
                }
            }
        val must = markers.filter { it.second == "E" }.map { it.first }.toSet()
        val may = markers.filter { it.second == "E?" }.map { it.first }.toSet()
        return ConformanceCase(header.id, header.rule, header.source, body, must, may)
    }
}
