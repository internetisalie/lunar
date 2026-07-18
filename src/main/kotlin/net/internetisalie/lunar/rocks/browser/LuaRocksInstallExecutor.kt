package net.internetisalie.lunar.rocks.browser

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.LuaRocksEnvironment
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService
import java.nio.file.Path

/** Install request carrying the canonical target tree (3-arg-cap context object, contract §3). */
data class InstallRequest(val name: String, val version: String?, val treeRoot: Path)

/**
 * Runs canonical `install` / `remove` on a background [Task.Backgroundable] against the project
 * rock tree (ROCKS-16-02, design §2.2). Builds args via [LuaRocksInstallCommand] and runs the CLI
 * with `--tree <root>` plus a working directory of the tree's parent.
 *
 * Threading contract (review finding #64): the CLI capture runs on the task's **background** thread;
 * [onInstall]/[onRemove] pass a callback that this executor invokes **on the EDT** via `invokeLater`.
 * Unlike the old handler, the KDoc promise matches the invocation site.
 */
class LuaRocksInstallExecutor(private val project: Project) {

    /** Internal execution context (3-arg-cap object, contract §3): all state for one CLI job. */
    private data class Job(
        val title: String,
        val treeRoot: Path,
        val args: List<String>,
        val successLabel: String,
        val onDone: (Boolean) -> Unit,
    )

    /** Installs [request]; [onDone] is invoked on the EDT with the success flag. */
    fun install(request: InstallRequest, onDone: (Boolean) -> Unit) {
        val title = request.version?.let { "Installing ${request.name} $it" } ?: "Installing ${request.name}"
        val args = LuaRocksInstallCommand.buildInstallArgs(request.treeRoot, request.name, request.version)
        runInBackground(Job(title, request.treeRoot, args, "installed ${request.name}", onDone))
    }

    /** Removes [name] from [treeRoot]; [onDone] is invoked on the EDT with the success flag. */
    fun remove(name: String, treeRoot: Path, onDone: (Boolean) -> Unit) {
        val args = LuaRocksInstallCommand.buildRemoveArgs(treeRoot, name)
        runInBackground(Job("Removing $name", treeRoot, args, "removed $name", onDone))
    }

    private fun runInBackground(job: Job) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, job.title, true) {
            override fun run(indicator: ProgressIndicator) = execute(job, indicator)
        })
    }

    private fun execute(job: Job, indicator: ProgressIndicator) {
        val exe = LuaRocksEnvironment.resolveExecutable(project) ?: return finish(false, NOT_CONFIGURED, job.onDone)
        val command = GeneralCommandLine(exe, *job.args.toTypedArray())
            .withWorkDirectory(job.treeRoot.parent?.toString())
        val output = LuaToolExecutionService.getInstance().capture(command, LuaExecTimeout.INSTALL, indicator = indicator)
        if (output.exitCode == 0) {
            LuaRocksSearchCache.invalidateAll()
            finish(true, "LuaRocks: ${job.successLabel}", job.onDone)
        } else {
            finish(false, "LuaRocks failed: ${output.stderr.trim().ifEmpty { "(no output)" }}", job.onDone)
        }
    }

    private fun finish(success: Boolean, message: String, onDone: (Boolean) -> Unit) {
        notify(message, if (success) NotificationType.INFORMATION else NotificationType.ERROR)
        ApplicationManager.getApplication().invokeLater { onDone(success) }
    }

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, type)
            .notify(project)
    }

    private companion object {
        const val NOTIFICATION_GROUP = "notification.group.lunar.luarocks"
        const val NOT_CONFIGURED =
            "LuaRocks is not configured. Register or bind it under " +
                "Settings | Languages & Frameworks | Lua | Toolchain."
    }
}
