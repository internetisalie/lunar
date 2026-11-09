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
class LuaRocksToolWindowFactory :
    ToolWindowFactory,
    DumbAware {
    /**
     * The stripe title is set when the tool window registers, not when its content is first
     * created: until then the stripe would show the registration id (BUG-67).
     */
    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = TITLE
    }

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        toolWindow.title = TITLE
        val panel = DependencyTreePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        ApplicationManager.getApplication().invokeLater { panel.refresh() }
    }

    private companion object {
        const val TITLE = "LuaRocks Dependencies"
    }
}
