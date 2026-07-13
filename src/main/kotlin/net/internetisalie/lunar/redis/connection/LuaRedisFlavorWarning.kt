package net.internetisalie.lunar.redis.connection

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.platform.LuaPlatform
import java.util.concurrent.ConcurrentHashMap

/**
 * Fires a single non-modal warning per connection per session when a connected server's detected
 * flavor disagrees with the project target platform (design §2.6, §3.4).
 *
 * A light project `@Service`: the platform injects [project]; the once-per-session guard is a
 * thread-safe `ConcurrentHashMap.newKeySet` keyed on connection id (cleared only on service dispose
 * = per session). The notification is error-bounded so a bus failure never breaks the connect flow
 * (engineering-contract §2).
 */
@Service(Service.Level.PROJECT)
class LuaRedisFlavorWarning(private val project: Project) {

    private val shownConnectionIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Warns once for [connectionId] iff [detected] mismatches [target] (design §3.4). */
    fun warnOnceIfMismatch(connectionId: String, detected: ServerFlavor, target: LuaPlatform) {
        if (!LuaRedisServerFlavor.mismatches(detected, target)) return
        if (!shownConnectionIds.add(connectionId)) return
        showMismatchNotification(detected, target)
    }

    private fun showMismatchNotification(detected: ServerFlavor, target: LuaPlatform) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(MISMATCH_TITLE, mismatchContent(detected, target), NotificationType.WARNING)
                .notify(project)
        } catch (failure: RuntimeException) {
            LOG.warn("Failed to show Redis/Valkey flavor mismatch notification", failure)
        }
    }

    private fun mismatchContent(detected: ServerFlavor, target: LuaPlatform): String =
        "Connected server is ${detected.name.lowercaseFlavor()} but the project target is " +
            "${target.label}. Consider switching the target platform so portability checks match."

    private fun String.lowercaseFlavor(): String = lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        private val LOG = Logger.getInstance(LuaRedisFlavorWarning::class.java)
        private const val NOTIFICATION_GROUP = "notification.group.lunar.tools"
        private const val MISMATCH_TITLE = "Redis/Valkey flavor mismatch"

        fun getInstance(project: Project): LuaRedisFlavorWarning = project.service()
    }
}
