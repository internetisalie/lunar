package net.internetisalie.lunar.toolchain.discovery

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import net.internetisalie.lunar.util.newProjectBackgroundTask

/**
 * On project open, offers a one-click **Adopt** for a detected, unknown env directory
 * (TOOLING-02-14, design §2.8). Detection runs off the EDT; adoption runs on a background task
 * (`registerTool` probes each binary and must not run on the EDT).
 *
 * Ships UNREGISTERED: the `plugin.xml` swap replacing `HererocksDetectStartup` lands in TOOLING-05
 * in one atomic commit, so both detectors never offer duplicate notifications during the transition.
 */
class LuaEnvironmentDetectionStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        try {
            val detected = LuaEnvironmentDetector.detect(project) ?: return
            if (LuaEnvironmentDetector.isKnownDirectory(project, detected)) return
            offerAdopt(project, detected)
        } catch (throwable: Throwable) {
            LOG.warn("environment detection failed", throwable)
        }
    }

    private fun offerAdopt(project: Project, directory: String) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                "Lua environment detected at $directory",
                NotificationType.INFORMATION
            )
        notification.addAction(object : NotificationAction("Adopt") {
            override fun actionPerformed(event: AnActionEvent, ignored: Notification) {
                newProjectBackgroundTask("Adopting Lua environment", project) {
                    LuaEnvironmentAdopter.adopt(project, directory)
                }.queue()
                notification.expire()
            }
        })
        notification.notify(project)
    }

    companion object {
        private val LOG = Logger.getInstance(LuaEnvironmentDetectionStartup::class.java)
        private const val NOTIFICATION_GROUP = "notification.group.lunar.tools"
    }
}
