package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Thin static facade over the [LuaProjectSettings] environment-set API (ROCKS-15, design §2.3) so
 * non-settings callers (the status-bar widget, the matrix action) never reach into `State`.
 */
object HererocksEnvSet {
    fun all(project: Project): List<HererocksEnvState> =
        LuaProjectSettings.getInstance(project).resolveAllEnvs()

    fun active(project: Project): HererocksEnvState? =
        LuaProjectSettings.getInstance(project).activeEnv()

    fun switch(project: Project, envId: String) =
        LuaProjectSettings.getInstance(project).setActiveEnvAndNotify(project, envId)
}
