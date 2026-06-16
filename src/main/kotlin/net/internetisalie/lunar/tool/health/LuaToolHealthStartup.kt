package net.internetisalie.lunar.tool.health

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Project startup activity that initialises the [LuaToolHealthMonitor] (TOOL-03, design §2.5).
 *
 * Registered as `<postStartupActivity>` in `plugin.xml`; called once per project open.
 * Kicks off the VFS listener and the initial revalidation pass in the background.
 */
class LuaToolHealthStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        val monitor = LuaToolHealthMonitor.getInstance(project)
        monitor.start()
        monitor.revalidateAll()
    }
}
