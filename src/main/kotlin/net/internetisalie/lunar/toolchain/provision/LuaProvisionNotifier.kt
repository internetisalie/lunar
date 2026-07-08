package net.internetisalie.lunar.toolchain.provision

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Surfaces provisioning progress/result balloons (design §3.1). Extracted as a seam so the
 * orchestrator pipeline can be unit-tested without a live notification bus, and so all balloon
 * text is defined in one place.
 */
interface LuaProvisionNotifier {
    fun notify(project: Project, message: String, type: NotificationType)
}

/** Production notifier on the `notification.group.lunar.tools` group (`plugin.xml`). */
class BalloonProvisionNotifier : LuaProvisionNotifier {
    override fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, type)
            .notify(project)
    }

    private companion object {
        private const val NOTIFICATION_GROUP = "notification.group.lunar.tools"
    }
}
