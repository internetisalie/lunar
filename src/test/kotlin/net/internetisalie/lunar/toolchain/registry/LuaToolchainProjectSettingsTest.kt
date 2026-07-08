package net.internetisalie.lunar.toolchain.registry

import com.intellij.util.xmlb.XmlSerializer
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaToolchainProjectSettingsTest : ToolchainSettingsTestCase() {

    @Test
    fun testStateRoundTripsDeepEqual_TC8() {
        val original = LuaToolchainProjectState().apply {
            bindings["luacheck"] = "tool-b"
            environments.add(
                LuaEnvironmentState(
                    id = "env-1",
                    name = "dev",
                    rootDir = "/proj/.lua/env-1",
                    toolIds = mutableListOf("tool-r", "tool-p")
                )
            )
            activeEnvironmentId = "env-1"
            kindOptions[LuaKindOptionKeys.LUACHECK_ARGUMENTS] = "--std max"
        }

        val element = XmlSerializer.serialize(original)
        val restored = XmlSerializer.deserialize(element, LuaToolchainProjectState::class.java)

        assertEquals(original.bindings, restored.bindings)
        assertEquals(original.activeEnvironmentId, restored.activeEnvironmentId)
        assertEquals(original.kindOptions, restored.kindOptions)
        assertEquals(1, restored.environments.size)

        val restoredEnv = restored.environments.first()
        assertEquals("env-1", restoredEnv.id)
        assertEquals("dev", restoredEnv.name)
        assertEquals("/proj/.lua/env-1", restoredEnv.rootDir)
        assertEquals(mutableListOf("tool-r", "tool-p"), restoredEnv.toolIds)
    }

    @Test
    fun testActiveEnvironmentNullWhenBlank() {
        val settings = LuaToolchainProjectSettings.getInstance(project)
        settings.loadState(
            LuaToolchainProjectState().apply {
                environments.add(LuaEnvironmentState(id = "env-1", rootDir = "/proj/.lua"))
                activeEnvironmentId = ""
            }
        )

        assertNull(settings.activeEnvironment())
    }

    @Test
    fun testActiveEnvironmentNullWhenDangling() {
        val settings = LuaToolchainProjectSettings.getInstance(project)
        settings.loadState(
            LuaToolchainProjectState().apply {
                environments.add(LuaEnvironmentState(id = "env-1", rootDir = "/proj/.lua"))
                activeEnvironmentId = "env-missing"
            }
        )

        assertNull(settings.activeEnvironment())
    }

    @Test
    fun testActiveEnvironmentResolvesMatchingId() {
        val settings = LuaToolchainProjectSettings.getInstance(project)
        settings.loadState(
            LuaToolchainProjectState().apply {
                environments.add(LuaEnvironmentState(id = "env-1", name = "a", rootDir = "/proj/a"))
                environments.add(LuaEnvironmentState(id = "env-2", name = "b", rootDir = "/proj/b"))
                activeEnvironmentId = "env-2"
            }
        )

        val active = settings.activeEnvironment()
        assertNotNull(active)
        assertEquals("env-2", active?.id)
        assertEquals("b", active?.name)
    }

    @Test
    fun testEnvironmentsReturnsRecordedList() {
        val settings = LuaToolchainProjectSettings.getInstance(project)
        settings.loadState(
            LuaToolchainProjectState().apply {
                environments.add(LuaEnvironmentState(id = "env-1", rootDir = "/proj/a"))
                environments.add(LuaEnvironmentState(id = "env-2", rootDir = "/proj/b"))
            }
        )

        assertEquals(listOf("env-1", "env-2"), settings.environments().map { it.id })
    }

    @Test
    fun testUpsertAndActivateDirDedup_TC10() {
        val events = recordEvents()

        val first = settings.upsertEnvironmentAndActivate(
            LuaEnvironmentState(id = "", name = "first", rootDir = "/p/.lua", toolIds = mutableListOf("t1"))
        )
        val second = settings.upsertEnvironmentAndActivate(
            LuaEnvironmentState(id = "other", name = "second", rootDir = "/p/./.lua", toolIds = mutableListOf("t2"))
        )

        assertEquals(first.id, second.id)
        assertEquals(1, settings.environments().size)

        val record = settings.environments().first()
        assertEquals(first.id, record.id)
        assertEquals("second", record.name)
        assertEquals(mutableListOf("t2"), record.toolIds)
        assertEquals(first.id, settings.state.activeEnvironmentId)

        synchronized(events) {
            val changes = events.map { it.change }
            assertEquals(
                listOf(
                    LuaToolchainChange.ENVIRONMENT_ADDED,
                    LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED,
                    LuaToolchainChange.ENVIRONMENT_UPDATED
                ),
                changes
            )
        }
    }

    @Test
    fun testActivateUnknownIsNoOp_TC11() {
        settings.loadState(
            LuaToolchainProjectState().apply {
                environments.add(LuaEnvironmentState(id = "e1", rootDir = "/p/e1"))
                activeEnvironmentId = "e1"
            }
        )
        val events = recordEvents()

        val result = settings.activateEnvironment("nope")

        assertFalse(result)
        assertEquals("e1", settings.state.activeEnvironmentId)
        synchronized(events) {
            assertEquals(0, events.size)
        }
    }

    @Test
    fun testActivationNeverTouchesBindings_TC12() {
        val toolB = seedTool("luacheck")
        val toolC = seedTool("luacheck", environmentId = "envc")
        settings.setBinding("luacheck", toolB.id)

        settings.upsertEnvironmentAndActivate(
            LuaEnvironmentState(id = "envc", rootDir = "/p/envc", toolIds = mutableListOf(toolC.id))
        )
        assertEquals(mapOf("luacheck" to toolB.id), settings.state.bindings)

        settings.deactivateEnvironment()
        assertEquals(mapOf("luacheck" to toolB.id), settings.state.bindings)
        assertEquals("", settings.state.activeEnvironmentId)

        val fieldNames = LuaToolchainProjectState::class.java.declaredFields.map { it.name }.toSet()
        assertEquals(setOf("bindings", "environments", "activeEnvironmentId", "kindOptions"), fieldNames)
    }

    @Test
    fun testEffectiveKindOptionPrecedence_TC15() {
        registry.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, "--std max")

        assertEquals("--std max", settings.effectiveKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS))

        settings.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, "--no-color")

        assertEquals("--no-color", settings.effectiveKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS))
    }

    @Test
    fun testRemoveEnvironmentUnregistersOwnedTools_TC16() {
        val toolR = seedTool("lua", environmentId = "envE")
        val toolP = seedTool("luarocks", environmentId = "envE")
        val toolA = seedTool("luacheck")

        settings.upsertEnvironmentAndActivate(
            LuaEnvironmentState(
                id = "envE",
                rootDir = "/p/envE",
                toolIds = mutableListOf(toolR.id, toolP.id)
            )
        )
        val events = recordEvents()

        settings.removeEnvironment("envE", deleteDir = false)

        assertTrue(settings.environments().isEmpty())
        assertEquals("", settings.state.activeEnvironmentId)

        val remainingIds = registry.tools().map { it.id }.toSet()
        assertFalse(remainingIds.contains(toolR.id))
        assertFalse(remainingIds.contains(toolP.id))
        assertTrue(remainingIds.contains(toolA.id))

        synchronized(events) {
            val changes = events.map { it.change }
            assertTrue(changes.contains(LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED))
            assertTrue(changes.contains(LuaToolchainChange.ENVIRONMENT_REMOVED))
            assertTrue(changes.contains(LuaToolchainChange.TOOL_REMOVED))
            val activeIdx = changes.indexOf(LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED)
            val removedIdx = changes.indexOf(LuaToolchainChange.ENVIRONMENT_REMOVED)
            assertTrue(activeIdx < removedIdx)
        }
    }
}
