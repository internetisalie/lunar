package net.internetisalie.lunar.coverage

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.rt.coverage.data.LineData
import com.intellij.rt.coverage.data.ProjectData

class LuaCoverageTest : BasePlatformTestCase() {

    @Test
    fun testLuaCovStatsParser() {
        val statsContent = """
            5:initrd/usr/bin/tests.lua
            0 1 0 2 0
            3:src/math.lua
            10 0 5
        """.trimIndent()

        val tempFile = File.createTempFile("luacov.stats", "out")
        try {
            tempFile.writeText(statsContent)
            val results = LuaCovStatsParser.parse(tempFile)
            
            assertEquals(2, results.size)
            
            val testFile = results.find { it.filePath == "initrd/usr/bin/tests.lua" } ?: throw AssertionError("initrd/usr/bin/tests.lua not found")
            assertEquals(5, testFile.lineHits.size)
            assertEquals(0, testFile.lineHits[1])
            assertEquals(1, testFile.lineHits[2])
            assertEquals(0, testFile.lineHits[3])
            assertEquals(2, testFile.lineHits[4])
            assertEquals(0, testFile.lineHits[5])

            val mathFile = results.find { it.filePath == "src/math.lua" } ?: throw AssertionError("src/math.lua not found")
            assertEquals(3, mathFile.lineHits.size)
            assertEquals(10, mathFile.lineHits[1])
            assertEquals(0, mathFile.lineHits[2])
            assertEquals(5, mathFile.lineHits[3])
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testLuaCovReportParser() {
        val reportContent = """
            ==============================================================================
            initrd/usr/bin/tests.lua
            ==============================================================================
                 -- comment line
               1 local test_modules = {}
            ***0 local fs = require("runtime.fs")
              10 for line in p:lines() do
        """.trimIndent()

        val tempFile = File.createTempFile("luacov.report", "out")
        try {
            tempFile.writeText(reportContent)
            val results = LuaCovReportParser.parse(tempFile)
            
            assertEquals(1, results.size)
            val testFile = results[0]
            assertEquals("initrd/usr/bin/tests.lua", testFile.filePath)
            
            // Only executable lines should be parsed:
            // Line 1: comment (non-exec, skipped)
            // Line 2: 1 hit
            // Line 3: ***0 (0 hits)
            // Line 4: 10 hits
            assertEquals(3, testFile.lineHits.size)
            assertEquals(1, testFile.lineHits[2])
            assertEquals(0, testFile.lineHits[3])
            assertEquals(10, testFile.lineHits[4])
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testLuaCovReportParserVariableAsteriskWidth() {
        // luacov pads the hit-count column to its width, so the asterisk run that marks an
        // uncovered (0-hit) line varies in length (***0, ****0, ...). All such lines must parse
        // to 0 hits and enter lineHits, and toProjectData must emit a NONE-status LineData.
        val reportContent = """
            ==============================================================================
            src/widths.lua
            ==============================================================================
               1 local covered = 1
            ***0 local three = 2
            ****0 local four = 3
        """.trimIndent()

        val tempFile = File.createTempFile("luacov.report", "out")
        try {
            tempFile.writeText(reportContent)
            val results = LuaCovReportParser.parse(tempFile)

            assertEquals(1, results.size)
            val fileCoverage = results[0]
            assertEquals(1, fileCoverage.lineHits[1])
            // 3-asterisk uncovered line
            assertEquals(0, fileCoverage.lineHits[2])
            // 4-asterisk uncovered line (luacov's actual default) must not be dropped
            assertEquals(0, fileCoverage.lineHits[3])
            assertTrue(fileCoverage.lineHits.containsKey(3))

            val projectData = LuaCovReportParser.toProjectData(results)
            val classData = projectData.getClassData("src/widths.lua") ?: throw AssertionError("ClassData not found")

            val threeAsterisk = classData.getLineData(2) ?: throw AssertionError("Line 2 not found")
            assertEquals(LineCoverage.NONE.toInt(), threeAsterisk.status)

            val fourAsterisk = classData.getLineData(3) ?: throw AssertionError("Line 3 not found")
            assertEquals(0, fourAsterisk.hits)
            assertEquals(LineCoverage.NONE.toInt(), fourAsterisk.status)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testToProjectData() {
        val coverages = listOf(
            FileCoverage(
                filePath = "src/foo.lua",
                lineHits = mapOf(1 to 0, 2 to 5)
            )
        )
        val projectData = LuaCovReportParser.toProjectData(coverages)
        val classData = projectData.getClassData("src/foo.lua") ?: throw AssertionError("ClassData not found")
        
        val lines = classData.lines ?: throw AssertionError("Lines not found")
        assertEquals(3, lines.size) // indices 0, 1, 2
        
        val line1 = lines[1] as? LineData ?: throw AssertionError("Line 1 not found")
        assertEquals(0, line1.hits)

        val line2 = lines[2] as? LineData ?: throw AssertionError("Line 2 not found")
        assertEquals(5, line2.hits)
    }

    @Test
    fun testToProjectDataLineNumbersAreOneBasedAndConsistent() {
        // RUN-08-13: each LineData's own line number must match its array slot (1-based,
        // platform convention) so SimpleCoverageAnnotator/SrcFileAnnotator align hits to
        // editor lines. getLineData(L) must return the coverage for report line L.
        val coverages = listOf(
            FileCoverage(
                filePath = "src/bar.lua",
                lineHits = mapOf(1 to 0, 2 to 5, 7 to 3)
            )
        )
        val projectData = LuaCovReportParser.toProjectData(coverages)
        val classData = projectData.getClassData("src/bar.lua") ?: throw AssertionError("ClassData not found")

        val uncovered = classData.getLineData(1) ?: throw AssertionError("Line 1 not found")
        assertEquals(1, uncovered.lineNumber)
        assertEquals(0, uncovered.hits)

        val covered = classData.getLineData(2) ?: throw AssertionError("Line 2 not found")
        assertEquals(2, covered.lineNumber)
        assertEquals(5, covered.hits)

        val sparse = classData.getLineData(7) ?: throw AssertionError("Line 7 not found")
        assertEquals(7, sparse.lineNumber)
        assertEquals(3, sparse.hits)
    }

    @Test
    fun testToProjectDataSetsLineCoverageStatus() {
        // RUN-08-13: gutter overlays (CoverageLineMarkerRenderer) and project-tree
        // percentages (SimpleCoverageAnnotator.processLineData) are driven by
        // LineData.getStatus(), NOT by hits. Each LineData must carry an explicit
        // FULL/NONE status or no green/red stripe renders in the live editor.
        val coverages = listOf(
            FileCoverage(
                filePath = "src/bar.lua",
                lineHits = mapOf(1 to 0, 2 to 5)
            )
        )
        val projectData = LuaCovReportParser.toProjectData(coverages)
        val classData = projectData.getClassData("src/bar.lua") ?: throw AssertionError("ClassData not found")

        val uncovered = classData.getLineData(1) ?: throw AssertionError("Line 1 not found")
        assertEquals(LineCoverage.NONE.toInt(), uncovered.status)

        val covered = classData.getLineData(2) ?: throw AssertionError("Line 2 not found")
        assertEquals(LineCoverage.FULL.toInt(), covered.status)
    }
}
