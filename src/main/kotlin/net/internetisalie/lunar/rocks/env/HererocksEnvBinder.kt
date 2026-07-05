package net.internetisalie.lunar.rocks.env

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaInterpreterService
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.InterpreterMode
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.tool.LuaToolManager
import net.internetisalie.lunar.tool.LuaToolType
import java.io.File
import java.nio.file.Path

/**
 * Registers and binds the products of a provisioned hererocks environment (ROCKS-14-04) and clears
 * them again (ROCKS-14-08). The `bin/luarocks` launcher is registered through the existing
 * [LuaToolManager]; `bin/lua` becomes the project interpreter — so every downstream ROCKS feature
 * transparently targets the isolated env with no resolver change.
 */
object HererocksEnvBinder {
    private val LOG = Logger.getInstance(HererocksEnvBinder::class.java)
    private const val NOTIFICATION_GROUP = "notification.group.lunar.luarocks"

    /**
     * Binds the products of [spec] into the project. The `LUAROCKS` tool is always bound (so build /
     * matrix / rockspec features target the env regardless of mode). The interpreter + target are only
     * repointed when the project is in [InterpreterMode.HEREROCKS_MANAGED] (ROCKS-16) — in
     * [InterpreterMode.EXPLICIT] the user's manual interpreter/target are left untouched.
     *
     * Must be invoked off the EDT: the `lua -v` probe runs on the calling thread and only the settings
     * mutations are marshalled back via `invokeLater`.
     */
    fun bind(project: Project, spec: HererocksEnvState) {
        try {
            LocalFileSystem.getInstance().refreshAndFindFileByPath(spec.directory)
            val tool = LuaToolManager.getInstance().registerTool(spec.luarocksExe(), LuaToolType.LUAROCKS)
            if (tool == null) {
                notify(project, "luarocks not found in provisioned env: ${spec.luarocksExe()}", NotificationType.ERROR)
                return
            }
            val settings = LuaProjectSettings.getInstance(project)
            val managed = settings.interpreterMode == InterpreterMode.HEREROCKS_MANAGED
            val interpreter = if (managed) {
                LuaInterpreter(Path.of(spec.luaExe())).also { LuaInterpreterService.getInstance().identify(it) }
            } else null
            val target = if (managed) spec.toTarget() else null
            ApplicationManager.getApplication().invokeLater {
                settings.setProjectToolBindingAndNotify(LuaToolType.LUAROCKS.name, tool.id)
                if (managed) {
                    val previousLevel = settings.state.languageLevel
                    settings.setInterpreterAndNotify(interpreter)
                    target?.let { settings.setTargetAndNotify(it) }
                    if (settings.state.languageLevel != previousLevel) PlatformLibraryIndex.reload()
                }
                notify(project, "Bound Lua environment ${spec.displayLabel()}", NotificationType.INFORMATION)
            }
        } catch (throwable: Throwable) {
            LOG.warn("Failed to bind hererocks env at ${spec.directory}", throwable)
            notify(project, "Failed to bind Lua environment: ${throwable.message}", NotificationType.ERROR)
        }
    }

    /**
     * Unbinds the active env: always clears the `LUAROCKS` tool binding and removes the env from the
     * set. In [InterpreterMode.HEREROCKS_MANAGED] it also hands interpreter/target ownership back to
     * the user via [LuaProjectSettings.setInterpreterModeAndNotify] — restoring the stashed explicit
     * choice (or project defaults) and flipping to [InterpreterMode.EXPLICIT]. In
     * [InterpreterMode.EXPLICIT] the interpreter/target are never touched (ROCKS-16).
     */
    fun unbind(project: Project, deleteDir: Boolean) {
        val settings = LuaProjectSettings.getInstance(project)
        val active = settings.activeEnv()
        val directory = active?.directory
        val managed = settings.interpreterMode == InterpreterMode.HEREROCKS_MANAGED
        ApplicationManager.getApplication().invokeLater {
            settings.setProjectToolBindingAndNotify(LuaToolType.LUAROCKS.name, null)
            if (managed) settings.setInterpreterModeAndNotify(project, InterpreterMode.EXPLICIT)
            if (active != null) settings.removeEnv(active.id)
        }
        if (deleteDir && directory != null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                FileUtil.delete(File(directory))
            }
        }
    }

    /** Normalized absolute-path key for directory-based env dedup (ROCKS-15 remediation). */
    fun normalizeDir(directory: String): String =
        FileUtil.toCanonicalPath(File(directory).absolutePath)

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, type)
            .notify(project)
    }
}
