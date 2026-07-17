package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings

/** Registers the active-Lua-environment status-bar widget (TOOLING-05-06, design §2.7). */
class LuaEnvStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = LuaEnvStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "Lua Environment"

    override fun createWidget(project: Project): StatusBarWidget = LuaEnvStatusBarWidget(project)

    /**
     * Gate on Lua-ness: show the widget only when the project has at least one configured Lua
     * environment (BUG-375). The check is a synchronized in-memory read — EDT-safe and O(1); no
     * disk I/O. Users can still add the widget manually via the status-bar context menu.
     */
    override fun isAvailable(project: Project): Boolean =
        LuaToolchainProjectSettings.getInstance(project).environments().isNotEmpty()

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
