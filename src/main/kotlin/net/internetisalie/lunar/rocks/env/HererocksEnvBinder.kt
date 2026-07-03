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

    fun bind(project: Project, spec: HererocksEnvState) {
        try {
            LocalFileSystem.getInstance().refreshAndFindFileByPath(spec.directory)
            val tool = LuaToolManager.getInstance().registerTool(spec.luarocksExe(), LuaToolType.LUAROCKS)
            if (tool == null) {
                notify(project, "luarocks not found in provisioned env: ${spec.luarocksExe()}", NotificationType.ERROR)
                return
            }
            val interpreter = LuaInterpreter(Path.of(spec.luaExe()))
                .also { LuaInterpreterService.getInstance().identify(it) }
            ApplicationManager.getApplication().invokeLater {
                val settings = LuaProjectSettings.getInstance(project)
                settings.setProjectToolBindingAndNotify(LuaToolType.LUAROCKS.name, tool.id)
                settings.setInterpreterAndNotify(interpreter)
                settings.state.hererocksEnv = spec
                notify(project, "Bound Lua environment ${spec.displayLabel()}", NotificationType.INFORMATION)
            }
        } catch (throwable: Throwable) {
            LOG.warn("Failed to bind hererocks env at ${spec.directory}", throwable)
            notify(project, "Failed to bind Lua environment: ${throwable.message}", NotificationType.ERROR)
        }
    }

    fun unbind(project: Project, deleteDir: Boolean) {
        val settings = LuaProjectSettings.getInstance(project)
        val directory = settings.state.hererocksEnv?.directory
        ApplicationManager.getApplication().invokeLater {
            settings.setProjectToolBindingAndNotify(LuaToolType.LUAROCKS.name, null)
            settings.setInterpreterAndNotify(null)
            settings.state.hererocksEnv = null
        }
        if (deleteDir && directory != null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                FileUtil.delete(File(directory))
            }
        }
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, type)
            .notify(project)
    }
}
