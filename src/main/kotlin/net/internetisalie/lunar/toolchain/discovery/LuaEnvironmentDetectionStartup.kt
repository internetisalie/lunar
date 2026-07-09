package net.internetisalie.lunar.toolchain.discovery

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.internetisalie.lunar.toolchain.provision.LuaEnvManifest
import net.internetisalie.lunar.util.newProjectBackgroundTask
import java.nio.file.Path

/**
 * On project open, offers a one-click **Adopt** for a detected **foreign** env directory
 * (TOOLING-02-14, design §2.8). Detection + the offer decision run off the EDT on
 * [Dispatchers.IO]; adoption runs on a background task (`registerTool` probes each binary and
 * must not run on the EDT).
 *
 * Deduped against [net.internetisalie.lunar.toolchain.provision.LuaEnvRedetectionStartup] (which
 * owns the re-registration prompt for orphaned **Lunar-provisioned** trees, marked by a
 * `.lunar-env.json` manifest): this startup skips any manifest-bearing directory so a single
 * project-open pass never fires two notifications for the same tree.
 */
class LuaEnvironmentDetectionStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        try {
            val detected = withContext(Dispatchers.IO) {
                LuaEnvironmentDetector.detect(project)?.takeIf { shouldOfferAdopt(project, it) }
            } ?: return
            offerAdopt(project, detected)
        } catch (throwable: Throwable) {
            LOG.warn("environment detection failed", throwable)
        }
    }

    /**
     * Adopt is offered only for a *foreign* env directory: one not already recorded and NOT a
     * Lunar-provisioned tree (a `.lunar-env.json` marker hands the re-registration prompt to
     * [net.internetisalie.lunar.toolchain.provision.LuaEnvRedetectionStartup]).
     */
    internal fun shouldOfferAdopt(project: Project, directory: String): Boolean {
        if (LuaEnvironmentDetector.isKnownDirectory(project, directory)) return false
        return LuaEnvManifest.read(Path.of(directory)) == null
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
