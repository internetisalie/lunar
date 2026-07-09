package net.internetisalie.lunar.rocks.init

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaRuntimeInfo
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaToolchainAppState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import java.util.UUID

/**
 * TOOLING-05 §2.8 / TC 13: New Project wizard runtime choice → project [Target] + a TOOLING-02
 * RUNTIME binding (explicit) or an activated TOOLING-02 environment (provision). No
 * `HererocksProvisioner`/`InterpreterMode` symbol is referenced.
 */
class LuaRocksInterpreterInitializerTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
            LuaToolchainProjectSettings.getInstance(project).loadState(LuaToolchainProjectState())
            LuaProjectSettings.getInstance(project).state.target = null
        } finally {
            super.tearDown()
        }
    }

    private fun seedRuntime(path: String): LuaRegisteredTool {
        val tool = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = "lua",
            path = path,
            version = "5.1.0",
            luaVersion = "5.1",
            runtime = LuaRuntimeInfo("Lua", "5.1.0", LuaLanguageLevel.LUA51, LuaPlatform.STANDARD, "Lua 5.1.0"),
            origin = Origin.MANUAL,
            environmentId = null,
            health = LuaToolHealth(fileExists = true, executable = true, probeOk = true, probedAtMtime = 1L, reason = null),
        )
        LuaToolchainRegistry.getInstance().registerProvisioned(tool)
        return tool
    }

    fun testExplicitPathBindsRegisteredRuntimeAndSetsTarget() {
        val tool = seedRuntime("/usr/bin/lua")
        val settings = LuaRocksProjectSettings(
            name = "lib",
            kindId = WizardRuntimeKinds.LUA,
            luaVersion = "5.1",
            provisionEnvironment = false,
            interpreterPath = "/usr/bin/lua",
        )

        LuaRocksInterpreterInitializer.applySettings(project, settings)

        val projectState = LuaProjectSettings.getInstance(project).state
        assertEquals(LuaPlatform.STANDARD, projectState.getTarget().platform)
        assertEquals("5.1", projectState.getTarget().version.label)
        assertEquals(LuaLanguageLevel.LUA51, projectState.languageLevel)
        assertEquals(tool.id, LuaToolchainProjectSettings.getInstance(project).binding("lua"))
    }

    fun testExplicitPathWithNoInterpreterLeavesNoBinding() {
        val settings = LuaRocksProjectSettings(name = "lib", provisionEnvironment = false, interpreterPath = "")

        LuaRocksInterpreterInitializer.applySettings(project, settings)

        assertNull(LuaToolchainProjectSettings.getInstance(project).binding("lua"))
    }

    fun testProvisionSetsTargetWithoutBinding() {
        val settings = LuaRocksProjectSettings(
            name = "lib",
            kindId = WizardRuntimeKinds.LUA,
            luaVersion = "5.5",
            provisionEnvironment = true,
        )

        LuaRocksInterpreterInitializer.applySettings(project, settings)

        val projectState = LuaProjectSettings.getInstance(project).state
        assertEquals(LuaPlatform.STANDARD, projectState.getTarget().platform)
        assertEquals("5.5", projectState.getTarget().version.label)
        assertEquals(LuaLanguageLevel.LUA55, projectState.languageLevel)
        // Provisioning binds nothing explicitly; the env drives the runtime once provisioned.
        assertNull(LuaToolchainProjectSettings.getInstance(project).binding("lua"))
    }

    fun testLuaJitKindMapsToLuaJitTarget() {
        val settings = LuaRocksProjectSettings(
            name = "lib",
            kindId = WizardRuntimeKinds.LUAJIT,
            luaVersion = "2.1",
            provisionEnvironment = true,
        )

        LuaRocksInterpreterInitializer.applySettings(project, settings)

        val projectState = LuaProjectSettings.getInstance(project).state
        assertEquals(LuaPlatform.LUAJIT, projectState.getTarget().platform)
        assertEquals("2.1", projectState.getTarget().version.label)
        assertEquals(LuaLanguageLevel.LUA51, projectState.languageLevel)
    }

    fun testScheduleProvisionActivatesEnvironment() {
        val settings = LuaRocksProjectSettings(
            name = "lib",
            kindId = WizardRuntimeKinds.LUA,
            luaVersion = "5.4",
            provisionEnvironment = true,
        )

        LuaRocksInterpreterInitializer.scheduleProvision(project, "/tmp/wizard-proj", settings)

        val active = LuaToolchainProjectSettings.getInstance(project).activeEnvironment()
        assertNotNull("provisioning activates the wizard environment", active)
        assertEquals("/tmp/wizard-proj/${LuaRocksInterpreterInitializer.ENV_DIR_NAME}", active?.rootDir)
    }
}
