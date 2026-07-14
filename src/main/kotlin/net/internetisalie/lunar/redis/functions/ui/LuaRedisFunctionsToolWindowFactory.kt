package net.internetisalie.lunar.redis.functions.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Registers the Redis Functions tool window and wires its content (design §2.10).
 *
 * Mirrors [net.internetisalie.lunar.rocks.ui.LuaRocksToolWindowFactory]: creates a single
 * [LuaRedisFunctionsPanel] content tab and schedules an initial refresh via `invokeLater`.
 */
class LuaRedisFunctionsToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = LuaRedisFunctionsPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        ApplicationManager.getApplication().invokeLater { panel.refresh() }
    }
}
