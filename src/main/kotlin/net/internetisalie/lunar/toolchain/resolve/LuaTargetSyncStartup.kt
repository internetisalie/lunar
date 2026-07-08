package net.internetisalie.lunar.toolchain.resolve

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class LuaTargetSyncStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        LuaTargetSynchronizer.getInstance(project).ensureSynchronized()
    }
}
