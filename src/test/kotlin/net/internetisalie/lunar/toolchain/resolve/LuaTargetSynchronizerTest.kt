package net.internetisalie.lunar.toolchain.resolve

import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.PlatformTestUtil
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainChange
import net.internetisalie.lunar.toolchain.registry.LuaToolchainEvent
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase

class LuaTargetSynchronizerTest : ToolchainSettingsTestCase() {

    private val synchronizer: LuaTargetSynchronizer
        get() = LuaTargetSynchronizer.getInstance(project)

    fun `test active environment runtime drives target and language level`() {
        val luajitRuntime = runtimeInfo(LuaPlatform.LUAJIT, "2.1.0-beta3", LuaLanguageLevel.LUA51)
        val luajitTool = seedTool("luajit", usable = true, environmentId = "E", runtime = luajitRuntime)
        settings.upsertEnvironmentAndActivate(
            LuaEnvironmentState(id = "E", name = "E", rootDir = "/p/.lua", toolIds = mutableListOf(luajitTool.id))
        )
        prepareBaseline()

        synchronizer.onEvent(event(LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED, environmentId = "E"))
        assertProjectTarget(LuaPlatform.LUAJIT, "2.1", LuaLanguageLevel.LUA51)

        val lua54Runtime = runtimeInfo(LuaPlatform.STANDARD, "5.4.6", LuaLanguageLevel.LUA54)
        val lua54Tool = seedTool("lua", usable = true, runtime = lua54Runtime)
        settings.setBinding("lua", lua54Tool.id)
        settings.deactivateEnvironment()

        synchronizer.onEvent(event(LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED))
        assertProjectTarget(LuaPlatform.STANDARD, "5.4", LuaLanguageLevel.LUA54)
    }

    fun `test inventory fallback runtime never drives target`() {
        val lua51Runtime = runtimeInfo(LuaPlatform.STANDARD, "5.1.5", LuaLanguageLevel.LUA51)
        seedTool("lua", usable = true, runtime = lua51Runtime)
        prepareBaseline()

        synchronizer.onEvent(event(LuaToolchainChange.TOOL_REGISTERED, kindId = "lua"))
        assertProjectTarget(LuaPlatform.STANDARD, "5.4", LuaLanguageLevel.LUA54)
    }

    fun `test synchronizer is inert for kind option change`() {
        val luajitRuntime = runtimeInfo(LuaPlatform.LUAJIT, "2.1.0-beta3", LuaLanguageLevel.LUA51)
        val luajitTool = seedTool("luajit", usable = true, environmentId = "E", runtime = luajitRuntime)
        settings.upsertEnvironmentAndActivate(
            LuaEnvironmentState(id = "E", name = "E", rootDir = "/p/.lua", toolIds = mutableListOf(luajitTool.id))
        )
        prepareBaseline()

        synchronizer.onEvent(event(LuaToolchainChange.KIND_OPTION_CHANGED, optionKey = "luacheck.arguments"))
        assertProjectTarget(LuaPlatform.STANDARD, "5.4", LuaLanguageLevel.LUA54)
    }

    fun `test synchronizer is inert for environment added without activation`() {
        val luajitRuntime = runtimeInfo(LuaPlatform.LUAJIT, "2.1.0-beta3", LuaLanguageLevel.LUA51)
        val luajitTool = seedTool("luajit", usable = true, environmentId = "E", runtime = luajitRuntime)
        settings.upsertEnvironment(
            LuaEnvironmentState(id = "E", name = "E", rootDir = "/p/.lua", toolIds = mutableListOf(luajitTool.id))
        )
        prepareBaseline()

        synchronizer.onEvent(event(LuaToolchainChange.ENVIRONMENT_ADDED, environmentId = "E"))
        assertProjectTarget(LuaPlatform.STANDARD, "5.4", LuaLanguageLevel.LUA54)
    }

    fun `test bindings are untouched across activate and deactivate`() {
        val luacheckTool = seedTool("luacheck", usable = true)
        settings.setBinding("luacheck", luacheckTool.id)
        val luajitRuntime = runtimeInfo(LuaPlatform.LUAJIT, "2.1.0-beta3", LuaLanguageLevel.LUA51)
        val luajitTool = seedTool("luajit", usable = true, environmentId = "E", runtime = luajitRuntime)
        settings.upsertEnvironmentAndActivate(
            LuaEnvironmentState(id = "E", name = "E", rootDir = "/p/.lua", toolIds = mutableListOf(luajitTool.id))
        )
        prepareBaseline()

        synchronizer.onEvent(event(LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED, environmentId = "E"))
        assertEquals(luacheckTool.id, settings.binding("luacheck"))

        settings.deactivateEnvironment()
        synchronizer.onEvent(event(LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED))
        assertEquals(luacheckTool.id, settings.binding("luacheck"))
    }

    private fun prepareBaseline() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            LuaProjectSettings.getInstance(project).setTargetAndNotify(
                PlatformVersionRegistry.resolveTarget(LuaPlatform.STANDARD, "5.4")
            )
        }
        synchronizer.resetGuardForTest()
    }

    private fun assertProjectTarget(
        platform: LuaPlatform,
        versionLabel: String,
        level: LuaLanguageLevel
    ) {
        val expected = PlatformVersionRegistry.resolveTarget(platform, versionLabel)
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            val state = LuaProjectSettings.getInstance(project).state
            assertEquals(expected, state.getTarget())
            assertEquals(level, state.languageLevel)
        }
    }

    private fun event(
        change: LuaToolchainChange,
        kindId: String? = null,
        environmentId: String? = null,
        optionKey: String? = null
    ): LuaToolchainEvent = LuaToolchainEvent(
        change = change,
        project = project,
        kindId = kindId,
        environmentId = environmentId,
        optionKey = optionKey
    )
}
