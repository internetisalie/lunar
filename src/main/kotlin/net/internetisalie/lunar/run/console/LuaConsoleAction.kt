package net.internetisalie.lunar.run.console

import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.util.newProjectBackgroundTask

/**
 * Tools-menu action that opens an interactive Lua REPL (RUN-03). Reports a notification when no
 * project interpreter is configured instead of failing silently.
 */
class LuaConsoleAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        newProjectBackgroundTask("Starting Lua Console", project) {
            try {
                LuaConsoleRunner(project).initAndRun()
            } catch (failure: ExecutionException) {
                ApplicationManager.getApplication().invokeLater { notifyFailure(project, failure.message) }
            }
        }.queue()
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.getData(CommonDataKeys.PROJECT) != null
    }

    private fun notifyFailure(project: Project, message: String?) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("notification.group.lunar.debugger")
            .createNotification(
                "Lua Console",
                message ?: "Configure a Lua interpreter in project settings.",
                NotificationType.ERROR,
            )
            .notify(project)
    }
}
