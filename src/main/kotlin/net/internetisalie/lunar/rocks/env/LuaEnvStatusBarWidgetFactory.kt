package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

/** Registers the active-Lua-environment status-bar widget (ROCKS-15-03, design §2.5). */
class LuaEnvStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = LuaEnvStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "Lua Environment"

    override fun createWidget(project: Project): StatusBarWidget = LuaEnvStatusBarWidget(project)

    override fun isAvailable(project: Project): Boolean = true

    override fun canBeEnabledOn(statusBar: com.intellij.openapi.wm.StatusBar): Boolean = true
}
