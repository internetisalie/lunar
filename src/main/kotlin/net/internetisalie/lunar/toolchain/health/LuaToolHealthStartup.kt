package net.internetisalie.lunar.toolchain.health

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Project-open bootstrap (design §2.3): starts the [LuaToolHealthMonitor] (VFS listener + topic
 * subscription) and runs an initial revalidation pass. Registered declaratively in `plugin.xml` by
 * TOOLING-07 Phase 3 (this phase leaves it unregistered by design).
 */
class LuaToolHealthStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        val monitor = LuaToolHealthMonitor.getInstance(project)
        monitor.start()
        monitor.revalidateAll()
    }
}
