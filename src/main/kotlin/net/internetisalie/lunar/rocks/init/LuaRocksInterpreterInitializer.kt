package net.internetisalie.lunar.rocks.init

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.rocks.env.HererocksEnvState
import net.internetisalie.lunar.rocks.env.HererocksProvisioner
import net.internetisalie.lunar.settings.InterpreterMode
import net.internetisalie.lunar.settings.LuaApplicationSettings
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Applies the New Project wizard's interpreter choice to a freshly generated project (ROCKS-17).
 *
 * Splits the work so the pure settings mutation is unit-testable independently of the background
 * hererocks provision:
 * - [applySettings] sets the project [net.internetisalie.lunar.platform.target.Target] and the
 *   [InterpreterMode]: Managed when provisioning (the env will drive the interpreter once bound),
 *   Explicit with the chosen existing interpreter otherwise.
 * - [scheduleProvision] kicks the hererocks provision after the project has opened (so it never
 *   races project initialization); the provision binds the env, which — in Managed mode — repoints
 *   the interpreter + target via [HererocksProvisioner] → [LuaProjectSettings.upsertAndActivate].
 */
object LuaRocksInterpreterInitializer {

    /** Directory (relative to the project base) of the wizard-provisioned isolated environment. */
    const val ENV_DIR_NAME = ".lua"

    fun applySettings(project: Project, settings: LuaRocksProjectSettings) {
        val state = LuaProjectSettings.getInstance(project).state
        // Flavor + version define the project target in both paths (see HererocksEnvState.toTarget).
        state.setTarget(HererocksEnvState(flavor = settings.flavor, luaVersion = settings.luaVersion).toTarget())
        // The wizard makes an explicit choice, so pin the migration flag and don't let load-time
        // re-seeding override it.
        state.interpreterModeMigrated = true
        if (settings.provisionHererocks) {
            state.interpreterMode = InterpreterMode.HEREROCKS_MANAGED
            // The interpreter is derived once the env is provisioned and bound (Managed cascade).
        } else {
            state.interpreterMode = InterpreterMode.EXPLICIT
            state.interpreter = settings.interpreterPath.trim().ifBlank { null }?.let { path ->
                LuaApplicationSettings.findInterpreter(path) ?: LuaInterpreter(path = path)
            }
        }
    }

    fun scheduleProvision(project: Project, baseDirPath: String, settings: LuaRocksProjectSettings) {
        if (!settings.provisionHererocks) return
        val spec = HererocksEnvState(
            directory = "$baseDirPath/$ENV_DIR_NAME",
            flavor = settings.flavor,
            luaVersion = settings.luaVersion,
            luarocksVersion = "latest",
        )
        StartupManager.getInstance(project).runAfterOpened {
            HererocksProvisioner.getInstance(project).provision(spec, HererocksProvisioner.Mode.CREATE)
        }
    }
}
