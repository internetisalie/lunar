package net.internetisalie.lunar.rocks.env

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * On project open, detects an unbound hererocks environment and offers a one-click **Bind**
 * (ROCKS-14-05). Detection runs off the EDT; the notification reuses the existing LuaRocks group.
 */
class HererocksDetectStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        try {
            val detected = HererocksEnvDetector.detect(project) ?: return
            if (LuaProjectSettings.getInstance(project).state.hererocksEnv?.directory == detected) return
            offerBind(project, detected)
        } catch (throwable: Throwable) {
            LOG.warn("hererocks detection failed", throwable)
        }
    }

    private fun offerBind(project: Project, directory: String) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                "hererocks environment detected at $directory",
                NotificationType.INFORMATION,
            )
        notification.addAction(object : NotificationAction("Bind") {
            override fun actionPerformed(event: AnActionEvent, ignored: com.intellij.notification.Notification) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    HererocksEnvBinder.bind(project, HererocksEnvDetector.descriptorFromDir(directory))
                }
                notification.expire()
            }
        })
        notification.notify(project)
    }

    companion object {
        private val LOG = Logger.getInstance(HererocksDetectStartup::class.java)
        private const val NOTIFICATION_GROUP = "notification.group.lunar.luarocks"
    }
}
