package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Registers the redesigned LuaRocks package browser tool window (ROCKS-16-01/-10, design §2.8 / §7).
 *
 * Replaces `LuaRocksPackageBrowserToolWindowFactory`. Hosts a [LuaRocksBrowserPanel] (two-tab
 * Marketplace/Installed surface) and sets an unambiguous stripe title so it is not confused with
 * the ROCKS-03 dependency tool window (BUG-366). The panel is the content disposer, so its
 * Alarm/JBHtmlPane are torn down with the content.
 */
class LuaRocksBrowserToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "LuaRocks Packages"
        toolWindow.title = "LuaRocks Packages"
        val panel = LuaRocksBrowserPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}
