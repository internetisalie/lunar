package net.internetisalie.lunar.rocks.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.xmlb.XmlSerializer
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaToolchainAppState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TestLuaRocksRunConfiguration : BaseDocumentTest() {

    @AfterTest
    fun resetToolchainState() {
        resetToolchain()
    }

    private fun newConfig(name: String = "Test Rocks"): LuaRocksRunConfiguration {
        val project = myFixture.project
        val configType = LuaRocksRunConfigurationType()
        val factory = LuaRocksRunConfigurationFactory(configType)
        return LuaRocksRunConfiguration(project, factory, name)
    }

    private fun resetToolchain() {
        LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
        LuaToolchainProjectSettings.getInstance(myFixture.project).loadState(LuaToolchainProjectState())
    }

    private fun bindLuaRocks(path: String): LuaRegisteredTool {
        resetToolchain()
        val tool = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = "luarocks",
            path = path,
            version = "3.0.0",
            luaVersion = null,
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = null,
            health = LuaToolHealth(fileExists = true, executable = true, probeOk = true, probedAtMtime = 1L, reason = null),
        )
        LuaToolchainRegistry.getInstance().registerProvisioned(tool)
        LuaToolchainProjectSettings.getInstance(myFixture.project).setBinding("luarocks", tool.id)
        return tool
    }

    /** TC-ROCKS-04-04: command-line assembly (design §3.1). */
    @Test
    fun testCommandLineAssembly() {
        val config = newConfig()
        config.command = "build"
        config.globalFlags = "--local"
        config.arguments = "--no-doc"
        config.rockspecPath = "app-1.rockspec"

        val commandLine = config.buildCommandLine("luarocks")

        assertEquals("luarocks", commandLine.exePath)
        assertEquals(
            listOf("--local", "build", "--no-doc", "app-1.rockspec"),
            commandLine.parametersList.list
        )
    }

    /** Blank command falls back to the default `make`. */
    @Test
    fun testBlankCommandDefaultsToMake() {
        val config = newConfig()
        config.command = ""
        val commandLine = config.buildCommandLine("luarocks")
        assertEquals(listOf("make"), commandLine.parametersList.list)
    }

    /** Rockspec is only appended for make/build (not for e.g. `list`). */
    @Test
    fun testRockspecOnlyForBuildCommands() {
        val config = newConfig()
        config.command = "list"
        config.rockspecPath = "app-1.rockspec"
        val commandLine = config.buildCommandLine("luarocks")
        assertEquals(listOf("list"), commandLine.parametersList.list)
    }

    /** Pass-parent-env is on by default (ROCKS-04-08). */
    @Test
    fun testPassParentEnvDefaultsOn() {
        val config = newConfig()
        assertEquals(true, config.environmentVariables?.isPassParentEnvs)
    }

    /** TC-ROCKS-04-03: serialization round-trip via XmlSerializer. */
    @Test
    fun testOptionsRoundTrip() {
        val source = newConfig()
        // Use a non-default command so it is emitted (the serializer omits default values).
        source.command = "build"
        source.globalFlags = "--tree lua_modules"
        source.rockspecPath = "\$PROJECT_DIR\$/my-app-scm-1.rockspec"
        source.arguments = "--no-doc"
        source.environmentVariables = EnvironmentVariablesData.create(
            mapOf("DEBUG" to "1"), true, null
        )

        val element = XmlSerializer.serialize(source.getOptions())

        // The XML carries the documented option tags (design §4.1).
        val optionNames = element.getChildren("option").mapNotNull { it.getAttributeValue("name") }
        assertTrue(optionNames.contains("command"), "command option present")
        assertTrue(optionNames.contains("globalFlags"), "globalFlags option present")
        assertTrue(optionNames.contains("environmentVariables"), "env option present")

        val target = newConfig("Restored")
        XmlSerializer.deserializeInto(target.getOptions(), element)

        assertEquals("build", target.command)
        assertEquals("--tree lua_modules", target.globalFlags)
        assertEquals("\$PROJECT_DIR\$/my-app-scm-1.rockspec", target.rockspecPath)
        assertEquals("--no-doc", target.arguments)
        assertEquals(mapOf("DEBUG" to "1"), target.environmentVariables?.envs)
        assertEquals(true, target.environmentVariables?.isPassParentEnvs)
    }

    /**
     * TC 6: `resolveAndBuildCommandLine` (the `startProcess` path) resolves the bound luarocks
     * binary via the toolchain and prepends the env-builder PATH dirs.
     */
    @Test
    fun testStartProcessResolvesBoundToolAndPrependsPath() {
        val toolsDir = FileUtil.createTempDirectory("lunar-luarocks", null)
        val luaRocksPath = File(toolsDir, "luarocks").absolutePath
        bindLuaRocks(luaRocksPath)

        val config = newConfig()
        config.command = "install"
        config.arguments = "penlight"

        val commandLine = config.resolveAndBuildCommandLine()

        assertEquals(luaRocksPath, commandLine.exePath)
        assertEquals(listOf("install", "penlight"), commandLine.parametersList.list)
        val pathEnv = commandLine.environment["PATH"]!!
        assertTrue(
            pathEnv.startsWith(toolsDir.absolutePath + File.pathSeparator) ||
                pathEnv == toolsDir.absolutePath,
            "env-builder must prepend the resolved tool dir to PATH (got: $pathEnv)",
        )
    }

    /** TC 6 (null branch): no usable luarocks resolves → `ExecutionException` (no default path). */
    @Test
    fun testStartProcessThrowsWhenNothingResolves() {
        resetToolchain()
        val config = newConfig()
        config.command = "install"

        try {
            config.resolveAndBuildCommandLine()
            fail("expected ExecutionException when no luarocks is configured")
        } catch (expected: ExecutionException) {
            assertTrue(expected.message?.contains("LuaRocks is not configured") == true)
        }
    }
}
