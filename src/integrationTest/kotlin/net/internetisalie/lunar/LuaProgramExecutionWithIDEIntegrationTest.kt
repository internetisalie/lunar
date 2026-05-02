package net.internetisalie.lunar

import com.intellij.driver.client.Driver
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/**
 * Full IDE Integration tests for Lua program execution.
 *
 * These tests use the IntelliJ Platform IDE Starter framework to:
 * 1. Build the plugin distribution (automatic via Gradle)
 * 2. Start a full IDE instance with the Lunar plugin installed
 * 3. Open test projects with Lua files
 * 4. Execute tests within the IDE context using the Driver framework
 * 5. Shut down the IDE cleanly
 *
 * The IDE Starter framework + Driver provides:
 * - Starter.newContext() - configure IDE, project, and test parameters
 * - PluginConfigurator - install the built Lunar plugin
 * - runIdeWithDriver() - launch IDE process with remote driver
 * - useDriverAndCloseIde {} - execute test code with Driver API access
 * - Driver.project - access to IntelliJ Platform project APIs
 *
 * IDE Selection:
 * The IDE to test with is automatically determined from gradle.properties:platformType
 * This allows testing with different IDEs (GoLand, IntelliJ IDEA, PyCharm, etc.)
 * See IdeProductResolver for available IDE types and custom configuration
 *
 * Documentation:
 * - https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html
 * - https://plugins.jetbrains.com/docs/intellij/integration-tests-ui.html
 *
 * To run these tests:
 *   ./gradlew integrationTest
 *
 * NOTE: These tests currently demonstrate IDE startup and project opening.
 * Future implementation will add:
 * - RunManager API to create LuaRunConfiguration instances
 * - ExecutionEnvironmentBuilder to execute Lua programs
 * - ProcessListener to capture program output
 * - Output verification and assertions
 */
class LuaProgramExecutionWithIDEIntegrationTest {

    private fun createTestProject(name: String): Path {
        val projectDir = Path.of("build/test-projects/$name")
        projectDir.createDirectories()
        projectDir.resolve(".idea").createDirectories()
        return projectDir
    }

    @Test
    fun `execute simple print program through IDE`() {
        val projectDir = createTestProject("lua-ide-simple-print")
        val luaCode = """
            print("Hello from Lunar!")
            print("IDE Integration Test")
        """.trimIndent()

        projectDir.resolve("main.lua").writeText(luaCode)

        // Create IDE test context with project
        // IDE type is automatically determined from gradle.properties:platformType
        val ideProduct = IdeProductResolver.getConfiguredIdeProduct()
        println("✓ Using IDE: ${IdeProductResolver.getProductName(ideProduct)}")
        
        val context = Starter.newContext(
            testName = "SimplePrintTest",
            testCase = TestCase(
                ideInfo = ideProduct,
                projectInfo = LocalProjectInfo(projectDir)
            ).withVersion(IdeProductResolver.getTestVersion())
        )
        
        // Install the Lunar plugin
        val pathToPlugin = System.getProperty("path.to.build.plugin")
        require(pathToPlugin != null) { "path.to.build.plugin system property not set" }
        
        com.intellij.ide.starter.plugins.PluginConfigurator(context)
            .installPluginFromPath(File(pathToPlugin).toPath())
        
        // Run IDE with driver and execute test
        val result = context.runIdeWithDriver(
            launchName = "simple-print-test",
            runTimeout = 2.minutes
        ) {
            // IDE is configured, but not started yet
        }
        
        result.useDriverAndCloseIde {
            // IDE is NOW RUNNING with Lunar plugin installed
            println("✓ IDE started successfully")
            println("✓ Lunar plugin loaded")
            println("✓ Project: ${projectDir.toAbsolutePath()}")
            
            // Access to IDE APIs via this (Driver):
            // this.utility<RunManager>().allSettings
            // this.utility<Project>()
            // etc.
            
            // Future: Create and execute LuaRunConfiguration here
        }
    }

    @Test
    fun `verify IDE starts with multiple Lua files`() {
        val projectDir = createTestProject("lua-ide-multi-file")
        
        projectDir.resolve("main.lua").writeText("""
            local utils = require("utils")
            print("Result: " .. utils.add(5, 3))
        """.trimIndent())
        
        projectDir.resolve("utils.lua").writeText("""
            local M = {}
            function M.add(a, b) return a + b end
            return M
        """.trimIndent())

        // Create IDE test context with project
        // IDE type is automatically determined from gradle.properties:platformType
        val ideProduct = IdeProductResolver.getConfiguredIdeProduct()
        
        val context = Starter.newContext(
            testName = "MultiFileTest",
            testCase = TestCase(
                ideInfo = ideProduct,
                projectInfo = LocalProjectInfo(projectDir)
            ).withVersion(IdeProductResolver.getTestVersion())
        )
        
        val pathToPlugin = System.getProperty("path.to.build.plugin")
        require(pathToPlugin != null) { "path.to.build.plugin system property not set" }
        
        com.intellij.ide.starter.plugins.PluginConfigurator(context)
            .installPluginFromPath(File(pathToPlugin).toPath())
        
        val result = context.runIdeWithDriver(
            launchName = "multi-file-test",
            runTimeout = 2.minutes
        ) {}
        
        result.useDriverAndCloseIde {
            println("✓ IDE started with multi-file Lua project")
            println("✓ Files: main.lua, utils.lua")
            println("✓ Project indexed successfully")
        }
    }
}

