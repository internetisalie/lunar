package net.internetisalie.lunar.project

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.settings.LuaSettingsChangedListener

/**
 * Listens for Lua settings changes and refreshes the platform library index and analysis.
 *
 * When the project target changes (e.g., switching from Lua 5.1 to Lua 5.4),
 * this listener reacts by reloading the platform library, invalidating caches,
 * and re-running code analysis.
 *
 * TOOLING-08 (review #41): this service is a real subscriber of [LuaSettingsChangedListener.TOPIC].
 * It is instantiated once per project by [net.internetisalie.lunar.toolchain.resolve.LuaTargetSyncStartup]
 * so the notification chain published by [net.internetisalie.lunar.settings.LuaProjectSettings.setTargetAndNotify]
 * (and the project configurable) actually fires. The message-bus connection is scoped to this service's
 * lifetime via [Disposable].
 */
@Service(Service.Level.PROJECT)
class LuaSettingsChangeListener(private val project: Project) : LuaSettingsChangedListener, Disposable {

    init {
        project.messageBus.connect(this).subscribe(LuaSettingsChangedListener.TOPIC, this)
    }

    override fun onSettingsChanged() {
        // Reload platform libraries when settings change
        // This invalidates caches and rescans dependencies
        // (REDIS-04 DR-03b: the type-snapshot cache is target-aware — see LuaTypesSnapshot.forFile —
        // so a target switch invalidates it automatically without a manual per-file drop here.)
        PlatformLibraryIndex.reload()

        // Restart code analysis for open files (TARGET-05)
        DaemonCodeAnalyzer.getInstance(project).restart("MAINT-03: settings changed")
    }

    override fun dispose() {}

    companion object {
        /** Forces instantiation so the constructor's topic subscription is live (review #41). */
        fun getInstance(project: Project): LuaSettingsChangeListener =
            project.getService(LuaSettingsChangeListener::class.java)
    }
}
