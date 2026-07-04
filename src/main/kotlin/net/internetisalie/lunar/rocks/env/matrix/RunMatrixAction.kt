package net.internetisalie.lunar.rocks.env.matrix

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import net.internetisalie.lunar.rocks.LuaRockspecDiscoveryService
import net.internetisalie.lunar.rocks.env.HererocksEnvSet
import java.nio.file.Path

/**
 * Runs the rockspec `test` command against every provisioned environment (ROCKS-15-04, design §2.7,
 * §3.3). Disabled when no env is provisioned or no rockspec is discovered. Aggregation runs on a
 * background task; the results table update marshals to the EDT.
 */
class RunMatrixAction : DumbAwareAction("Run Test Matrix…") {

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabled = project != null && HererocksEnvSet.all(project).isNotEmpty()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        try {
            val envs = HererocksEnvSet.all(project)
            if (envs.isEmpty()) {
                notify(project, "No Lua environments to run the matrix against", NotificationType.WARNING)
                return
            }
            ProgressManager.getInstance().run(matrixTask(project, envs))
        } catch (throwable: Throwable) {
            LOG.warn("Failed to launch test matrix", throwable)
            notify(project, "Failed to launch test matrix: ${throwable.message}", NotificationType.ERROR)
        }
    }

    private fun matrixTask(project: Project, envs: List<net.internetisalie.lunar.rocks.env.HererocksEnvState>) =
        object : Task.Backgroundable(project, "Running Lua test matrix", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.checkCanceled()
                val rockspec = firstRockspec(project) ?: run {
                    notify(project, "No rockspec discovered for the matrix", NotificationType.WARNING)
                    return
                }
                val request = MatrixRunner.Request("test", rockspec, envs)
                val result = MatrixRunner.execute(request, MatrixRunner.processRunner)
                publish(project, result)
            }
        }

    private fun firstRockspec(project: Project): Path? =
        LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths().firstOrNull()?.rockspec

    private fun publish(project: Project, result: MatrixResult) {
        ApplicationManager.getApplication().invokeLater {
            MatrixResultsToolWindow.getOrCreatePanel(project).setResult(result)
            ToolWindowManager.getInstance(project)
                .getToolWindow(MatrixResultsToolWindow.TOOL_WINDOW_ID)?.show(null)
            val summary = if (result.allPassed) "Test matrix passed" else "Test matrix had failures"
            val type = if (result.allPassed) NotificationType.INFORMATION else NotificationType.WARNING
            notify(project, summary, type)
        }
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, type)
            .notify(project)
    }

    companion object {
        private val LOG = Logger.getInstance(RunMatrixAction::class.java)
        private const val NOTIFICATION_GROUP = "notification.group.lunar.luarocks"
    }
}
