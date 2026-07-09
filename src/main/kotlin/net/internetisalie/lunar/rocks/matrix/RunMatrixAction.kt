package net.internetisalie.lunar.rocks.matrix

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
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs the rockspec `test` command against every provisioned environment (ROCKS-15-04, design §2.6,
 * §3.3). Disabled when no env is provisioned or no rockspec is discovered. Each env runs in its own
 * [Task.Backgroundable] so rows execute concurrently — one slow env does not block the rest. Each
 * row marshals its results-table update to the EDT as it finishes; the last row to complete emits
 * the aggregate notification.
 */
class RunMatrixAction : DumbAwareAction("Run Test Matrix…") {

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabled = project != null && environmentsOf(project).isNotEmpty()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        try {
            val envs = environmentsOf(project)
            if (envs.isEmpty()) {
                notify(project, "No Lua environments to run the matrix against", NotificationType.WARNING)
                return
            }
            val rockspec = firstRockspec(project) ?: run {
                notify(project, "No rockspec discovered for the matrix", NotificationType.WARNING)
                return
            }
            launchMatrix(MatrixRun(project, MatrixRunner.Request("test", rockspec, envs)))
        } catch (throwable: Throwable) {
            LOG.warn("Failed to launch test matrix", throwable)
            notify(project, "Failed to launch test matrix: ${throwable.message}", NotificationType.ERROR)
        }
    }

    private fun environmentsOf(project: Project) =
        LuaToolchainProjectSettings.getInstance(project).environments()

    /** Shared per-run context: the request, the mutable rows, and a completion counter. */
    private class MatrixRun(val project: Project, val request: MatrixRunner.Request) {
        val rows: List<MatrixRow> = request.envs.map { MatrixRow(it) }
        val remaining = AtomicInteger(rows.size)
    }

    private fun launchMatrix(run: MatrixRun) {
        run.rows.forEach { row -> ProgressManager.getInstance().run(rowTask(run, row)) }
    }

    private fun rowTask(run: MatrixRun, row: MatrixRow) =
        object : Task.Backgroundable(run.project, "Running Lua matrix: ${row.env.name}", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.checkCanceled()
                MatrixRunner.runRow(run.request, MatrixRunner.processRunner, row)
                onRowFinished(run)
            }
        }

    private fun onRowFinished(run: MatrixRun) {
        val result = MatrixResult(run.rows)
        val last = run.remaining.decrementAndGet() == 0
        ApplicationManager.getApplication().invokeLater {
            publishTable(run.project, result)
            if (last) notifyAggregate(run.project, result)
        }
    }

    private fun publishTable(project: Project, result: MatrixResult) {
        MatrixResultsToolWindow.MatrixResultsPanel.getInstance(project).setResult(result)
        ToolWindowManager.getInstance(project)
            .getToolWindow(MatrixResultsToolWindow.TOOL_WINDOW_ID)?.show(null)
    }

    private fun notifyAggregate(project: Project, result: MatrixResult) {
        val summary = if (result.allPassed) "Test matrix passed" else "Test matrix had failures"
        val type = if (result.allPassed) NotificationType.INFORMATION else NotificationType.WARNING
        notify(project, summary, type)
    }

    private fun firstRockspec(project: Project): Path? =
        LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths().firstOrNull()?.rockspec

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
