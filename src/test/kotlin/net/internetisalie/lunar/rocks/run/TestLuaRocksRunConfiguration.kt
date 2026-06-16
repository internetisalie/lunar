package net.internetisalie.lunar.rocks.run

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.util.xmlb.XmlSerializer
import net.internetisalie.lunar.BaseDocumentTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestLuaRocksRunConfiguration : BaseDocumentTest() {

    private fun newConfig(name: String = "Test Rocks"): LuaRocksRunConfiguration {
        val project = myFixture.project
        val configType = LuaRocksRunConfigurationType()
        val factory = LuaRocksRunConfigurationFactory(configType)
        return LuaRocksRunConfiguration(project, factory, name)
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

    @Test
    fun testSettingsDefaultExecutable() {
        assertEquals("luarocks", LuaRocksSettings.getInstance().executablePath)
    }
}
