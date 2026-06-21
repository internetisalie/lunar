package net.internetisalie.lunar.coverage

import com.intellij.openapi.project.Project
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.rt.coverage.data.LineData
import com.intellij.rt.coverage.data.ProjectData
import java.io.File

data class FileCoverage(
    val filePath: String,
    val lineHits: Map<Int, Int> // 1-indexed line -> hit count (0 = uncovered)
)

object LuaCovReportParser {
    private val BOUNDARY = Regex("^={10,}$")
    private val UNCOVERED = Regex("^\\*+0\\s")
    private val COVERED = Regex("^\\s*(\\d+)\\s")

    fun parse(reportFile: File): List<FileCoverage> {
        val lines = reportFile.readLines()
        val results = mutableListOf<FileCoverage>()
        var state = "SEARCH_HEADER"
        var currentFile: String? = null
        var currentHits = mutableMapOf<Int, Int>()
        var lineNumber = 0

        for (line in lines) {
            when (state) {
                "SEARCH_HEADER" -> {
                    if (BOUNDARY.matches(line)) {
                        state = "PARSE_PATH"
                    }
                }
                "PARSE_PATH" -> {
                    currentFile = line.trim()
                    state = "EXPECT_BOUNDARY"
                }
                "EXPECT_BOUNDARY" -> {
                    if (BOUNDARY.matches(line)) {
                        lineNumber = 0
                        currentHits = mutableMapOf()
                        state = "PARSE_LINES"
                    } else {
                        state = "SEARCH_HEADER"
                    }
                }
                "PARSE_LINES" -> {
                    if (BOUNDARY.matches(line)) {
                        if (currentFile != null) {
                            results.add(FileCoverage(currentFile, currentHits))
                        }
                        state = "PARSE_PATH"
                    } else {
                        lineNumber++
                        if (UNCOVERED.containsMatchIn(line)) {
                            currentHits[lineNumber] = 0
                        } else {
                            val coveredMatch = COVERED.find(line)
                            if (coveredMatch != null) {
                                val hits = coveredMatch.groupValues[1].toInt()
                                currentHits[lineNumber] = hits
                            }
                        }
                    }
                }
            }
        }
        if (state == "PARSE_LINES" && currentFile != null) {
            results.add(FileCoverage(currentFile, currentHits))
        }
        return results
    }

    fun toProjectData(coverages: List<FileCoverage>, project: Project? = null): ProjectData {
        val projectData = ProjectData()
        for (coverage in coverages) {
            val resolvedPath = if (project != null && !File(coverage.filePath).isAbsolute) {
                val basePath = project.basePath
                if (basePath != null) {
                    val resolvedFile = File(basePath, coverage.filePath)
                    resolvedFile.absolutePath
                } else {
                    coverage.filePath
                }
            } else {
                coverage.filePath
            }
            val classData = projectData.getOrCreateClassData(resolvedPath)
            val maxLine = coverage.lineHits.keys.maxOrNull() ?: 0
            val lineDataArray = arrayOfNulls<LineData>(maxLine + 1)
            for ((line, hits) in coverage.lineHits) {
                val lineData = LineData(line, null)
                lineData.hits = hits
                lineData.setStatus(if (hits > 0) LineCoverage.FULL else LineCoverage.NONE)
                lineDataArray[line] = lineData
            }
            classData.setLines(lineDataArray)
        }
        return projectData
    }
}
