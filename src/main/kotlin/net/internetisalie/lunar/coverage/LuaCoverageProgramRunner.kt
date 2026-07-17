package net.internetisalie.lunar.coverage

import com.intellij.coverage.CoverageExecutor
import com.intellij.coverage.CoverageHelper
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationAction
import com.intellij.execution.runners.RunContentBuilder
import net.internetisalie.lunar.run.test.LuaTestRunConfiguration
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver
import net.internetisalie.lunar.rocks.browser.InstallRequest
import net.internetisalie.lunar.rocks.browser.LuaRocksInstallCommand
import net.internetisalie.lunar.rocks.browser.LuaRocksInstallExecutor
import java.io.File

class LuaCoverageProgramRunner : GenericProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "LuaCoverageProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == CoverageExecutor.EXECUTOR_ID && profile is LuaTestRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val project = environment.project
        val luacovTool = LuaToolResolver.getInstance().resolve(project, "luacov")
        if (luacovTool == null) {
            val notificationGroup = NotificationGroupManager.getInstance()
                .getNotificationGroup("notification.group.lunar.tools")
            val notification = notificationGroup.createNotification(
                "Code coverage library 'luacov' is not installed in the current SDK.",
                NotificationType.ERROR
            )
            val treeRoot = LuaRocksInstallCommand.resolveTargetTree(project)
            if (treeRoot != null) {
                notification.addAction(NotificationAction.createSimple("Install via LuaRocks") {
                    notification.expire()
                    LuaRocksInstallExecutor(project).install(InstallRequest("luacov", null, treeRoot)) {
                        // Handled by background install task
                    }
                })
            }
            notification.notify(project)
            return null
        }

        val config = environment.runProfile as LuaTestRunConfiguration
        val workDir = if (!config.workingDirectory.isNullOrEmpty()) config.workingDirectory else project.basePath
        if (workDir != null) {
            val statsFile = File(workDir, "luacov.stats.out")
            if (statsFile.exists()) {
                statsFile.delete()
            }
        }

        val executionResult = state.execute(environment.executor, this) ?: return null
        val processHandler = executionResult.processHandler
        if (processHandler != null) {
            CoverageHelper.attachToProcess(config, processHandler, environment.runnerSettings)
        }

        return RunContentBuilder(executionResult, environment).showRunContent(environment.contentToReuse)
    }
}
