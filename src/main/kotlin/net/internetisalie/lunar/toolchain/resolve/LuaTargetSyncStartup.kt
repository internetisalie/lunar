package net.internetisalie.lunar.toolchain.resolve

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import net.internetisalie.lunar.project.LuaSettingsChangeListener

class LuaTargetSyncStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Instantiate the settings-change subscriber so the notification chain is live (review #41):
        // its constructor subscribes to LuaSettingsChangedListener.TOPIC.
        LuaSettingsChangeListener.getInstance(project)
        LuaTargetSynchronizer.getInstance(project).ensureSynchronized()
    }
}
