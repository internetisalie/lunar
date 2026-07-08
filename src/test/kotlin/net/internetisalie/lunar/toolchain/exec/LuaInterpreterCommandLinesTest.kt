package net.internetisalie.lunar.toolchain.exec

import com.intellij.execution.configurations.GeneralCommandLine
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.nio.file.Path
import java.util.UUID

/**
 * Tests for [LuaInterpreterCommandLines] (TOOLING-03-13 → TCs 18-19, plus `forProject`).
 *
 * `forBinary` is pure and uses plain assertions. `forProject` seeds a RUNTIME-kind tool into the
 * real TOOLING-02 registry with explicit health and binds it, so `resolveRuntime` returns it;
 * resolution never touches disk, so a nonexistent tool path is fine.
 */
class LuaInterpreterCommandLinesTest : ToolchainSettingsTestCase() {

    override fun setUp() {
        super.setUp()
        LuaProjectSettings.getInstance(project).state.sourcePath = ""
        LuaExecutionEnvironmentBuilder.getInstance(project).invalidate()
    }

    // TC 18
    fun testForBinaryPlainExecutable() {
        val cmd = LuaInterpreterCommandLines.forBinary(Path.of("/env/bin/lua"))

        assertEquals("/env/bin/lua", cmd.exePath)
        assertEquals(Path.of("/env/bin"), cmd.workingDirectory)
        assertEquals(GeneralCommandLine.ParentEnvironmentType.CONSOLE, cmd.parentEnvironmentType)
    }

    // TC 19
    fun testForBinaryJarUsesJavaClasspath() {
        val cmd = LuaInterpreterCommandLines.forBinary(Path.of("/opt/luaj.jar"))

        assertEquals("java", cmd.exePath)
        assertEquals(listOf("-cp", "/opt/luaj.jar", "lua"), cmd.parametersList.parameters)
    }

    fun testForProjectNullWhenNoRuntimeResolves() {
        assertNull(LuaInterpreterCommandLines.forProject(project))
    }

    fun testForProjectResolvesRuntimeAndAppliesEnvironment() {
        bindRuntime("/opt/lua/bin/lua")

        val cmd = LuaInterpreterCommandLines.forProject(project)
            ?: error("expected a command line for a bound runtime")

        assertEquals("/opt/lua/bin/lua", cmd.exePath)
        val path = cmd.environment["PATH"].orEmpty()
        assertTrue("expected the runtime dir prepended to PATH", path.startsWith("/opt/lua/bin"))
    }

    private fun bindRuntime(path: String): LuaRegisteredTool {
        val tool = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = "lua",
            path = path,
            version = "1.0.0",
            luaVersion = null,
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = null,
            health = LuaToolHealth(
                fileExists = true,
                executable = true,
                probeOk = true,
                probedAtMtime = 1L,
                reason = null,
            ),
        )
        registry.registerProvisioned(tool)
        settings.setBinding("lua", tool.id)
        LuaExecutionEnvironmentBuilder.getInstance(project).invalidate()
        return tool
    }
}
