package net.internetisalie.lunar.toolchain.resolve

import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase

class LuaToolResolverTest : ToolchainSettingsTestCase() {

    private val resolver: LuaToolResolver
        get() = LuaToolResolver.getInstance()

    fun `test project binding wins over inventory fallback`() {
        seedTool("luacheck", usable = true)
        val toolB = seedTool("luacheck", usable = true)
        settings.setBinding("luacheck", toolB.id)

        assertEquals(toolB.id, resolver.resolve(project, "luacheck")?.id)
        val detailed = resolver.resolveDetailed(project, "luacheck")
        assertTrue(detailed is LuaToolResolution.Resolved)
        assertEquals(ResolutionSource.PROJECT_BINDING, (detailed as LuaToolResolution.Resolved).source)
    }

    fun `test precedence cascade from environment down to inventory`() {
        val toolA = seedTool("luacheck", usable = true)
        val toolB = seedTool("luacheck", usable = true)
        settings.setBinding("luacheck", toolB.id)
        val toolC = seedTool("luacheck", usable = true)
        settings.upsertEnvironmentAndActivate(
            LuaEnvironmentState(id = "E", name = "E", rootDir = "/p/.lua", toolIds = mutableListOf(toolC.id))
        )

        val active = resolver.resolveDetailed(project, "luacheck")
        assertTrue(active is LuaToolResolution.Resolved)
        assertEquals(toolC.id, (active as LuaToolResolution.Resolved).tool.id)
        assertEquals(ResolutionSource.ACTIVE_ENVIRONMENT, active.source)

        settings.deactivateEnvironment()
        assertEquals(toolB.id, resolver.resolve(project, "luacheck")?.id)

        settings.setBinding("luacheck", null)
        registry.setGlobalBinding("luacheck", toolA.id)
        val global = resolver.resolveDetailed(project, "luacheck")
        assertTrue(global is LuaToolResolution.Resolved)
        assertEquals(toolA.id, (global as LuaToolResolution.Resolved).tool.id)
        assertEquals(ResolutionSource.GLOBAL_BINDING, global.source)

        registry.setGlobalBinding("luacheck", null)
        val fallback = resolver.resolveDetailed(project, "luacheck")
        assertTrue(fallback is LuaToolResolution.Resolved)
        assertEquals(toolA.id, (fallback as LuaToolResolution.Resolved).tool.id)
        assertEquals(ResolutionSource.INVENTORY_FALLBACK, fallback.source)
    }

    fun `test empty inventory yields unresolved with message`() {
        val detailed = resolver.resolveDetailed(project, "luacheck")
        assertTrue(detailed is LuaToolResolution.Unresolved)
        val unresolved = detailed as LuaToolResolution.Unresolved
        assertEquals("luacheck", unresolved.kindId)
        assertTrue(unresolved.skipped.isEmpty())

        val message = resolver.notConfiguredMessage("luacheck")
        assertTrue(message.contains("luacheck"))
        assertTrue(message.contains("Settings | Languages & Frameworks | Lua | Toolchain"))

        assertNull(resolver.resolve(project, "luacheck"))
    }

    fun `test stale project binding not in inventory falls through to global`() {
        settings.setBinding("luacheck", "B")

        val beforeGlobal = resolver.resolveDetailed(project, "luacheck")
        assertTrue(beforeGlobal is LuaToolResolution.Unresolved)
        assertTrue(
            (beforeGlobal as LuaToolResolution.Unresolved).skipped.any {
                it.tier == ResolutionSource.PROJECT_BINDING &&
                    it.toolId == "B" &&
                    it.reason == SkipReason.NOT_IN_INVENTORY
            }
        )

        val toolA = seedTool("luacheck", usable = true)
        registry.setGlobalBinding("luacheck", toolA.id)

        val detailed = resolver.resolveDetailed(project, "luacheck")
        assertTrue(detailed is LuaToolResolution.Resolved)
        val resolved = detailed as LuaToolResolution.Resolved
        assertEquals(toolA.id, resolved.tool.id)
        assertEquals(ResolutionSource.GLOBAL_BINDING, resolved.source)
    }

    fun `test unusable project binding is skipped and falls through`() {
        val toolB = seedTool("luacheck", usable = false)
        settings.setBinding("luacheck", toolB.id)

        val detailed = resolver.resolveDetailed(project, "luacheck")
        assertTrue(detailed is LuaToolResolution.Unresolved)
        assertTrue(
            (detailed as LuaToolResolution.Unresolved).skipped.any {
                it.tier == ResolutionSource.PROJECT_BINDING &&
                    it.toolId == toolB.id &&
                    it.reason == SkipReason.UNUSABLE
            }
        )
    }

    fun `test resolveIn is strict and never falls back to bindings`() {
        val toolR = seedTool("lua", usable = true)
        val toolP = seedTool("luarocks", usable = true)
        val toolB = seedTool("luacheck", usable = true)
        settings.setBinding("luacheck", toolB.id)
        val environment = LuaEnvironmentState(
            id = "E",
            name = "E",
            rootDir = "/p/.lua",
            toolIds = mutableListOf(toolR.id, toolP.id)
        )

        assertEquals(toolP.id, resolver.resolveIn(environment, "luarocks")?.id)
        assertNull(resolver.resolveIn(environment, "luacheck"))
    }

    fun `test resolveRuntime respects single-runtime invariant`() {
        val runtime54 = runtimeInfo(LuaPlatform.STANDARD, "5.4.0", LuaLanguageLevel.LUA54)
        val runtimeJit = runtimeInfo(LuaPlatform.LUAJIT, "2.1.0-beta3", LuaLanguageLevel.LUA51)
        val toolL = seedTool("lua", usable = true, runtime = runtime54)
        val toolJ = seedTool("luajit", usable = true, runtime = runtimeJit)

        settings.setBinding("luajit", toolJ.id)
        assertEquals(toolJ.id, resolver.resolveRuntime(project)?.id)

        settings.setBinding("lua", toolL.id)
        assertEquals(toolL.id, settings.binding("lua"))
        assertNull(settings.binding("luajit"))
        assertEquals(toolL.id, resolver.resolveRuntime(project)?.id)
    }
}
