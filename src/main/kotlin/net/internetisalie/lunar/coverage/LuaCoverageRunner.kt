package net.internetisalie.lunar.coverage

import com.intellij.coverage.CoverageEngine
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.CoverageSuite
import com.intellij.coverage.CoverageLoadErrorReporter
import com.intellij.coverage.CoverageLoadingResult
import com.intellij.coverage.SuccessCoverageLoadingResult
import com.intellij.coverage.FailedCoverageLoadingResult
import java.io.File

class LuaCoverageRunner : CoverageRunner() {
    override fun loadCoverageData(
        sessionDataFile: File,
        baseCoverageSuite: CoverageSuite?,
        reporter: CoverageLoadErrorReporter
    ): CoverageLoadingResult {
        if (!sessionDataFile.exists()) {
            return FailedCoverageLoadingResult("Coverage data file does not exist: ${sessionDataFile.path}")
        }
        return try {
            val lines = sessionDataFile.readLines()
            val hasStatsHeader = lines.firstOrNull()?.trim()?.contains(Regex("""^\d+:.+$""")) ?: false
            val coverages = if (hasStatsHeader) {
                LuaCovStatsParser.parse(sessionDataFile)
            } else {
                LuaCovReportParser.parse(sessionDataFile)
            }
            val projectData = LuaCovReportParser.toProjectData(coverages, baseCoverageSuite?.project)
            SuccessCoverageLoadingResult(projectData)
        } catch (e: Exception) {
            FailedCoverageLoadingResult(e.message ?: "Failed to load coverage data")
        }
    }

    override fun getPresentableName(): String = "LuaCov"

    override fun getId(): String = "LuaCov"

    override fun getDataFileExtension(): String = "out"

    override fun acceptsCoverageEngine(engine: CoverageEngine): Boolean =
        engine is LuaCoverageEngine
}
