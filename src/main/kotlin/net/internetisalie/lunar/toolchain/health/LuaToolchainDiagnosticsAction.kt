package net.internetisalie.lunar.toolchain.health

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.util.newProjectBackgroundTask

private val LOG: Logger = Logger.getInstance(LuaToolchainDiagnosticsAction::class.java)

private const val NOTIFICATION_GROUP = "notification.group.lunar.tools"

/**
 * Tools-menu action that triggers a full toolchain diagnostics snapshot on demand (design §2.6).
 * Runs [LuaToolDiagnostics.logSnapshot] on a background thread so the IDE log receives the output
 * without blocking the EDT.
 */
class LuaToolchainDiagnosticsAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val targetProject = e.project ?: return
        try {
            newProjectBackgroundTask("Lua: toolchain diagnostics", targetProject) {
                runDiagnostics(targetProject)
            }.queue()
        } catch (ex: Exception) {
            LOG.warn("Failed to queue toolchain diagnostics task", ex)
        }
    }

    private fun runDiagnostics(targetProject: Project) {
        try {
            LuaToolDiagnostics.logSnapshot(targetProject)
            notifyDone(targetProject)
        } catch (ex: Exception) {
            LOG.warn("Toolchain diagnostics snapshot failed", ex)
        }
    }

    private fun notifyDone(targetProject: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                "Toolchain diagnostics written to idea.log",
                NotificationType.INFORMATION
            )
            .notify(targetProject)
    }
}
