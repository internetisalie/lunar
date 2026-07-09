package net.internetisalie.lunar.toolchain.provision

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import java.nio.file.Path

/**
 * On project open, offers one-click re-registration of an orphaned Lunar-provisioned tree
 * (TOOLING-04-16 — successor to the removed legacy env-detect startup). Scans the likely env root
 * `<projectBase>/.lua` off the EDT for a `.lunar-env.json` whose `environmentId` has no matching
 * TOOLING-02 record; if found, a notification (group `notification.group.lunar.tools`) offers
 * "Re-register", which registers the recorded tools + environment via [RegistryProvisionResultSink]
 * on a pooled thread. All filesystem work runs on [Dispatchers.IO]; no EDT blocking.
 */
class LuaEnvRedetectionStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        try {
            val orphan = withContext(Dispatchers.IO) { detectOrphan(project) } ?: return
            offerReRegistration(project, orphan.rootDir, orphan.manifest)
        } catch (throwable: Throwable) {
            LOG.warn("Lunar environment re-detection failed", throwable)
        }
    }

    private data class Orphan(val rootDir: Path, val manifest: LuaEnvManifest)

    private fun detectOrphan(project: Project): Orphan? {
        val rootDir = guessEnvRoot(project) ?: return null
        val registeredIds = LuaToolchainProjectSettings.getInstance(project).environments().map { it.id }.toSet()
        val manifest = LuaEnvRedetection.findOrphan(rootDir, registeredIds) ?: return null
        return Orphan(rootDir, manifest)
    }

    private fun guessEnvRoot(project: Project): Path? =
        project.guessProjectDir()?.toNioPath()?.resolve(ENV_DIR_NAME)

    private fun offerReRegistration(project: Project, rootDir: Path, manifest: LuaEnvManifest) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                "Lunar environment '${manifest.environmentName}' found at $rootDir but not registered",
                NotificationType.INFORMATION,
            )
        notification.addAction(reRegisterAction(project, rootDir, manifest, notification))
        notification.notify(project)
    }

    private fun reRegisterAction(project: Project, rootDir: Path, manifest: LuaEnvManifest, notification: Notification) =
        object : NotificationAction("Re-register") {
            override fun actionPerformed(event: AnActionEvent, ignored: Notification) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    runCatching { RegistryProvisionResultSink().register(project, LuaEnvRedetection.toResult(rootDir, manifest)) }
                        .onFailure { LOG.warn("Re-registration failed for $rootDir", it) }
                }
                notification.expire()
            }
        }

    private companion object {
        private val LOG = Logger.getInstance(LuaEnvRedetectionStartup::class.java)
        private const val NOTIFICATION_GROUP = "notification.group.lunar.tools"
        private const val ENV_DIR_NAME = ".lua"
    }
}
