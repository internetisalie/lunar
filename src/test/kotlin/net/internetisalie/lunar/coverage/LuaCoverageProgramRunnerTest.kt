package net.internetisalie.lunar.coverage

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

class LuaCoverageProgramRunnerTest : BasePlatformTestCase() {

    // MAINT-32-03 TC-07: a stale luacov.stats.out is removed before the run.
    fun testClearStaleStatsDeletesExistingFile() {
        val workDir = Files.createTempDirectory("lunar_coverage").toFile()
        val stats = File(workDir, "luacov.stats.out").also { it.writeText("stale") }
        assertTrue(stats.exists())

        LuaCoverageProgramRunner().clearStaleStats(workDir.absolutePath)

        assertFalse("stale stats file must be deleted before the coverage run", stats.exists())
    }

    // MAINT-32-03 TC-07: absent stats file is a benign no-op (no exception).
    fun testClearStaleStatsIsNoOpWhenAbsent() {
        val workDir = Files.createTempDirectory("lunar_coverage_empty").toFile()
        LuaCoverageProgramRunner().clearStaleStats(workDir.absolutePath)
        assertFalse(File(workDir, "luacov.stats.out").exists())
    }
}
