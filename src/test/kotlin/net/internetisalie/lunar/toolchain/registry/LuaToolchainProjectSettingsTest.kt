package net.internetisalie.lunar.toolchain.registry

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaToolchainProjectSettingsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        LuaToolchainProjectSettings.getInstance(project).loadState(LuaToolchainProjectState())
    }

    override fun tearDown() {
        try {
            LuaToolchainProjectSettings.getInstance(project).loadState(LuaToolchainProjectState())
        } finally {
            super.tearDown()
        }
    }

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
}
