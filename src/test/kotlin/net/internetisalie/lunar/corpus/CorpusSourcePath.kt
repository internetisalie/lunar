package net.internetisalie.lunar.corpus

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Snapshots and restores the shared light project's `sourcePath`, so a member's
 * [CorpusEntry.moduleRoot] never reaches a later member (MAINT-37-09, BUG-496; design §2.13).
 */
object CorpusSourcePath {
    fun snapshot(project: Project): String = LuaProjectSettings.getInstance(project).state.sourcePath

    /** Writes [saved] back; true when the value had changed, so the caller must refresh roots. */
    fun restore(
        project: Project,
        saved: String,
    ): Boolean {
        val state = LuaProjectSettings.getInstance(project).state
        if (state.sourcePath == saved) return false
        state.sourcePath = saved
        return true
    }
}
