package net.internetisalie.lunar.toolchain.registry

import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaToolchainEventsTest : ToolchainSettingsTestCase() {

    @Test
    fun testSetBindingFiresExactlyOnce_TC9() {
        val toolB = seedTool("luacheck")
        val events = recordEvents()

        settings.setBinding("luacheck", toolB.id)
        settings.setBinding("luacheck", toolB.id)

        synchronized(events) {
            assertEquals(1, events.size)
            val event = events.first()
            assertEquals(LuaToolchainChange.PROJECT_BINDING_CHANGED, event.change)
            assertEquals(project, event.project)
            assertEquals("luacheck", event.kindId)
            assertEquals(toolB.id, event.toolId)
        }
    }

    @Test
    fun testSetBindingNullOnAbsentIsSilent() {
        val events = recordEvents()

        settings.setBinding("luacheck", null)

        synchronized(events) {
            assertEquals(0, events.size)
        }
    }

    @Test
    fun testSetBindingClearFiresOnce() {
        val toolB = seedTool("luacheck")
        settings.setBinding("luacheck", toolB.id)
        val events = recordEvents()

        settings.setBinding("luacheck", null)

        synchronized(events) {
            assertEquals(1, events.size)
            assertEquals(LuaToolchainChange.PROJECT_BINDING_CHANGED, events.first().change)
            assertNull(events.first().toolId)
        }
    }

    @Test
    fun testSingleRuntimeInvariantProjectScope_TC7() {
        val runtimeJit = seedTool("luajit")
        val runtimeLua = seedTool("lua")
        settings.setBinding("luajit", runtimeJit.id)
        val events = recordEvents()

        settings.setBinding("lua", runtimeLua.id)

        val bindings = settings.state.bindings
        assertEquals(runtimeLua.id, bindings["lua"])
        assertFalse(bindings.containsKey("luajit"))
        synchronized(events) {
            assertEquals(1, events.size)
            val event = events.first()
            assertEquals(LuaToolchainChange.PROJECT_BINDING_CHANGED, event.change)
            assertEquals("lua", event.kindId)
            assertEquals(runtimeLua.id, event.toolId)
        }
    }

    @Test
    fun testSingleRuntimeInvariantGlobalScope() {
        val runtimeJit = seedTool("luajit")
        val runtimeLua = seedTool("lua")
        registry.setGlobalBinding("luajit", runtimeJit.id)
        val events = recordEvents()

        registry.setGlobalBinding("lua", runtimeLua.id)

        val globals = registry.globalBindings()
        assertEquals(runtimeLua.id, globals["lua"])
        assertFalse(globals.containsKey("luajit"))
        synchronized(events) {
            assertEquals(1, events.size)
            val event = events.first()
            assertEquals(LuaToolchainChange.GLOBAL_BINDING_CHANGED, event.change)
            assertNull(event.project)
            assertEquals("lua", event.kindId)
        }
    }

    @Test
    fun testUpsertNewFiresEnvironmentAdded() {
        val events = recordEvents()

        val resolved = settings.upsertEnvironment(LuaEnvironmentState(rootDir = "/p/.lua"))

        synchronized(events) {
            assertEquals(1, events.size)
            val event = events.first()
            assertEquals(LuaToolchainChange.ENVIRONMENT_ADDED, event.change)
            assertEquals(project, event.project)
            assertEquals(resolved.id, event.environmentId)
        }
    }

    @Test
    fun testUpsertUnchangedMergeIsSilent() {
        val added = settings.upsertEnvironment(LuaEnvironmentState(rootDir = "/p/.lua"))
        val events = recordEvents()

        settings.upsertEnvironment(added.copy())

        synchronized(events) {
            assertEquals(0, events.size)
        }
    }

    @Test
    fun testSetKindOptionProjectScopeFiresKindOptionChanged() {
        val events = recordEvents()

        settings.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, "--no-color")

        synchronized(events) {
            assertEquals(1, events.size)
            val event = events.first()
            assertEquals(LuaToolchainChange.KIND_OPTION_CHANGED, event.change)
            assertEquals(project, event.project)
            assertEquals(LuaKindOptionKeys.LUACHECK_ARGUMENTS, event.optionKey)
        }
    }

    @Test
    fun testSetKindOptionBlankRemovesAndIsSilentWhenAbsent() {
        val events = recordEvents()

        settings.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, "   ")

        synchronized(events) {
            assertEquals(0, events.size)
        }
    }

    @Test
    fun testSetKindOptionUnchangedIsSilent() {
        settings.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, "--std max")
        val events = recordEvents()

        settings.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, "--std max")

        synchronized(events) {
            assertEquals(0, events.size)
        }
    }

    @Test
    fun testAppKindOptionChangedProjectNull() {
        val events = recordEvents()

        registry.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, "--std max")

        synchronized(events) {
            assertEquals(1, events.size)
            val event = events.first()
            assertEquals(LuaToolchainChange.KIND_OPTION_CHANGED, event.change)
            assertNull(event.project)
            assertEquals(LuaKindOptionKeys.LUACHECK_ARGUMENTS, event.optionKey)
        }
    }
}
