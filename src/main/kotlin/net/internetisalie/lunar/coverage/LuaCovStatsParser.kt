package net.internetisalie.lunar.coverage

import java.io.File

object LuaCovStatsParser {
    fun parse(statsFile: File): List<FileCoverage> {
        val lines = statsFile.readLines()
        val results = mutableListOf<FileCoverage>()
        var i = 0
        val headerRegex = Regex("""^(\d+):(.+)$""")
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                continue
            }
            val match = headerRegex.matchEntire(line)
            if (match != null) {
                val totalLines = match.groupValues[1].toInt()
                val filePath = match.groupValues[2]
                i++
                val hitCounts = mutableListOf<Int>()
                while (i < lines.size && hitCounts.size < totalLines) {
                    val nextLine = lines[i].trim()
                    if (nextLine.isEmpty()) {
                        i++
                        continue
                    }
                    if (headerRegex.matches(nextLine)) {
                        break
                    }
                    val parts = nextLine.split(Regex("""\s+""")).filter { it.isNotEmpty() }
                    for (part in parts) {
                        val count = part.toIntOrNull() ?: 0
                        hitCounts.add(count)
                        if (hitCounts.size == totalLines) {
                            break
                        }
                    }
                    i++
                }
                val lineHits = mutableMapOf<Int, Int>()
                for (j in 0 until hitCounts.size) {
                    lineHits[j + 1] = hitCounts[j]
                }
                results.add(FileCoverage(filePath, lineHits))
            } else {
                i++
            }
        }
        return results
    }
}
