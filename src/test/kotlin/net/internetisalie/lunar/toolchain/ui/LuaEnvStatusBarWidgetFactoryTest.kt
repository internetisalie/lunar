package net.internetisalie.lunar.toolchain.ui

import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase

/**
 * BUG-375: [LuaEnvStatusBarWidgetFactory.isAvailable] must return false for projects with no
 * configured Lua environment and true once an environment is registered.
 */
class LuaEnvStatusBarWidgetFactoryTest : ToolchainSettingsTestCase() {

    private val factory = LuaEnvStatusBarWidgetFactory()

    fun `test isAvailable returns false when no environments configured BUG375`() {
        // Fresh state: no environments
        assertFalse(
            "Widget must not appear in projects with no Lua environments (BUG-375)",
            factory.isAvailable(project),
        )
    }

    fun `test isAvailable returns true after adding an environment BUG375`() {
        settings.upsertEnvironmentAndActivate(
            LuaEnvironmentState(id = "E1", name = "lua-5.4", rootDir = "/p/e1"),
        )
        assertTrue(
            "Widget must appear when at least one Lua environment is configured (BUG-375)",
            factory.isAvailable(project),
        )
    }
}
