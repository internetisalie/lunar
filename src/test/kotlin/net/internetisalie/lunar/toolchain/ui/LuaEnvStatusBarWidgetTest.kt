package net.internetisalie.lunar.toolchain.ui

import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainChange
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase

/**
 * TOOLING-05 Phase 5: the env status-bar widget reads/switches the TOOLING-02 active environment.
 * Covers the active-env name label, the empty-set fallback, the popup model, and that switching an
 * env updates `activeEnvironmentId` and fires [LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED].
 */
class LuaEnvStatusBarWidgetTest : ToolchainSettingsTestCase() {

    fun `test text shows the active environment name`() {
        settings.upsertEnvironmentAndActivate(
            LuaEnvironmentState(id = "B", name = "Lua 5.3", rootDir = "/p/b"),
        )
        assertEquals("Lua 5.3", LuaEnvStatusBarWidget(project).getText())
    }

    fun `test text falls back when no active environment`() {
        assertEquals(LuaEnvStatusBarWidget.NO_ENV_TEXT, LuaEnvStatusBarWidget(project).getText())
    }

    fun `test popup model lists all envs plus the add item`() {
        val a = LuaEnvironmentState(id = "A", name = "A", rootDir = "/p/a")
        val b = LuaEnvironmentState(id = "B", name = "B", rootDir = "/p/b")
        val items = LuaEnvStatusBarWidget.popupItems(listOf(a, b))

        assertEquals(3, items.size)
        assertEquals(listOf(a, b), items.dropLast(1))
        assertEquals(LuaEnvStatusBarWidget.ADD_ENV_TEXT, items.last())
        assertFalse(LuaEnvStatusBarWidget.isActive(a, "B"))
        assertTrue(LuaEnvStatusBarWidget.isActive(b, "B"))
        assertFalse(LuaEnvStatusBarWidget.isActive(LuaEnvStatusBarWidget.ADD_ENV_TEXT, "B"))
    }

    fun `test switching activates the env and fires the topic`() {
        settings.upsertEnvironmentAndActivate(LuaEnvironmentState(id = "A", name = "A", rootDir = "/p/a"))
        settings.upsertEnvironment(LuaEnvironmentState(id = "B", name = "B", rootDir = "/p/b"))
        val events = recordEvents()

        assertTrue(settings.activateEnvironment("B"))

        assertEquals("B", settings.activeEnvironment()?.id)
        assertEquals("B", LuaEnvStatusBarWidget(project).getText())
        assertTrue(events.any { it.change == LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED && it.environmentId == "B" })
    }
}
