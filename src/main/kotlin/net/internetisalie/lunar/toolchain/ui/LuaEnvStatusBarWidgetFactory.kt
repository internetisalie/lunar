package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

/** Registers the active-Lua-environment status-bar widget (TOOLING-05-06, design §2.7). */
class LuaEnvStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = LuaEnvStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "Lua Environment"

    override fun createWidget(project: Project): StatusBarWidget = LuaEnvStatusBarWidget(project)

    override fun isAvailable(project: Project): Boolean = true

    override fun canBeEnabledOn(statusBar: com.intellij.openapi.wm.StatusBar): Boolean = true
}
