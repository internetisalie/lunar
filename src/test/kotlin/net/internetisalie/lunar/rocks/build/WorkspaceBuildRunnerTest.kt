package net.internetisalie.lunar.rocks.build

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.rocks.run.LuaRocksSettings
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class WorkspaceBuildRunnerTest : BasePlatformTestCase() {

    private var originalExecutable: String = ""
    private lateinit var fakeExecutable: File
    private lateinit var tempDir: Path
    private lateinit var logFile: File

    override fun setUp() {
        super.setUp()
        originalExecutable = LuaRocksSettings.getInstance().executablePath
        tempDir = Files.createTempDirectory("lunar-build-test")
        logFile = File(tempDir.toFile(), "build-log.txt")

        // Create fake luarocks executable
        fakeExecutable = File.createTempFile("fake-luarocks", ".sh").apply {
            writeText(
                """
                #!/bin/sh
                echo "Running luarocks with args: ${'$'}@" >> "${logFile.absolutePath}"
                echo "Working directory: ${'$'}(pwd)" >> "${logFile.absolutePath}"
                for arg in "${'$'}@"; do
                    if echo "${'$'}arg" | grep -q "fail-b-1.0-1.rockspec"; then
                        echo "Simulating failure for B" >> "${logFile.absolutePath}"
                        exit 2
                    fi
                done
                exit 0
                """.trimIndent()
            )
            setExecutable(true)
        }
        LuaRocksSettings.getInstance().executablePath = fakeExecutable.absolutePath
    }

    override fun tearDown() {
        try {
            LuaRocksSettings.getInstance().executablePath = originalExecutable
            if (::fakeExecutable.isInitialized) {
                fakeExecutable.delete()
            }
            if (::tempDir.isInitialized) {
                tempDir.toFile().deleteRecursively()
            }
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testBuildSuccessAllRocks() {
        val dirA = Files.createDirectories(tempDir.resolve("a"))
        val dirB = Files.createDirectories(tempDir.resolve("b"))
        val dirC = Files.createDirectories(tempDir.resolve("c"))

        val specA = Files.createFile(dirA.resolve("a-1.0-1.rockspec"))
        val specB = Files.createFile(dirB.resolve("b-1.0-1.rockspec"))
        val specC = Files.createFile(dirC.resolve("c-1.0-1.rockspec"))

        val rockA = WorkspaceRock("a", specA, emptyList())
        val rockB = WorkspaceRock("b", specB, listOf("a"))
        val rockC = WorkspaceRock("c", specC, listOf("b"))

        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        try {
            val outcome = WorkspaceBuildRunner.run(
                project,
                listOf(rockA, rockB, rockC),
                console,
                EmptyProgressIndicator()
            )

            assertEquals(3, outcome.builtCount)
            assertNull(outcome.failedRock)
            assertNull(outcome.exitCode)

            val logLines = logFile.readLines()
            assertTrue(logLines.any { it.contains("Running luarocks with args: make $specA") })
            assertTrue(logLines.any { it.contains("Working directory: ${dirA.toAbsolutePath()}") })
            assertTrue(logLines.any { it.contains("Running luarocks with args: make $specB") })
            assertTrue(logLines.any { it.contains("Working directory: ${dirB.toAbsolutePath()}") })
            assertTrue(logLines.any { it.contains("Running luarocks with args: make $specC") })
            assertTrue(logLines.any { it.contains("Working directory: ${dirC.toAbsolutePath()}") })
        } finally {
            console.dispose()
        }
    }

    @Test
    fun testBuildFailureStopsSubsequent() {
        val dirA = Files.createDirectories(tempDir.resolve("a"))
        val dirB = Files.createDirectories(tempDir.resolve("b"))
        val dirC = Files.createDirectories(tempDir.resolve("c"))

        val specA = Files.createFile(dirA.resolve("a-1.0-1.rockspec"))
        val specB = Files.createFile(dirB.resolve("fail-b-1.0-1.rockspec"))
        val specC = Files.createFile(dirC.resolve("c-1.0-1.rockspec"))

        val rockA = WorkspaceRock("a", specA, emptyList())
        val rockB = WorkspaceRock("b", specB, listOf("a"))
        val rockC = WorkspaceRock("c", specC, listOf("b"))

        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        try {
            val outcome = WorkspaceBuildRunner.run(
                project,
                listOf(rockA, rockB, rockC),
                console,
                EmptyProgressIndicator()
            )

            assertEquals(1, outcome.builtCount)
            assertEquals(rockB, outcome.failedRock)
            assertEquals(2, outcome.exitCode)

            val logLines = logFile.readLines()
            assertTrue(logLines.any { it.contains("Running luarocks with args: make $specA") })
            assertTrue(logLines.any { it.contains("Working directory: ${dirA.toAbsolutePath()}") })
            assertTrue(logLines.any { it.contains("Running luarocks with args: make $specB") })
            assertTrue(logLines.any { it.contains("Working directory: ${dirB.toAbsolutePath()}") })
            assertFalse(logLines.any { it.contains("Running luarocks with args: make $specC") })
            assertTrue(logLines.any { it.contains("Simulating failure for B") })
        } finally {
            console.dispose()
        }
    }
}
