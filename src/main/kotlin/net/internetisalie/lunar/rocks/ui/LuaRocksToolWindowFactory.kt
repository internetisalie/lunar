package net.internetisalie.lunar.rocks.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Registers the LuaRocks dependency tool window. The actual UI lives in [DependencyTreePanel]; this
 * factory wires it into a single content tab and kicks off the first resolution.
 */
class LuaRocksToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DependencyTreePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        ApplicationManager.getApplication().invokeLater { panel.refresh() }
    }
}
