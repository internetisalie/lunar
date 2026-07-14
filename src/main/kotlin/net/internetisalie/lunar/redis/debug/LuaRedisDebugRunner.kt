package net.internetisalie.lunar.redis.debug

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import net.internetisalie.lunar.redis.run.LuaRedisExecMode
import net.internetisalie.lunar.redis.run.LuaRedisRunConfiguration

/**
 * Starts an LDB debug session for a [LuaRedisRunConfiguration] under the Debug executor (design §2.1).
 *
 * Mirrors `run/LuaDebugRunner`: [canRun] gates on the Debug executor **and** a Redis Script config, so
 * this runner never collides with the MobDebug `LuaDebugRunner` (standard Lua) or the coverage runner.
 * [doExecute] runs the state, then builds the session via [XDebuggerManager.newSessionBuilder],
 * yielding a [LuaRedisDebugProcess]. Runs on the EDT (platform contract) and returns a descriptor.
 */
class LuaRedisDebugRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, runProfile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID &&
            runProfile is LuaRedisRunConfiguration &&
            runProfile.execMode != LuaRedisExecMode.FCALL

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val executionResult = state.execute(environment.executor, this) ?: return null
        val config = environment.runProfile as? LuaRedisRunConfiguration ?: return null
        return try {
            startSession(environment, executionResult, config)
        } catch (failure: ExecutionException) {
            log.info("Failed to create Redis debug session: ${failure.message}")
            notifyFailure(environment.project, failure)
            null
        }
    }

    private fun startSession(
        environment: ExecutionEnvironment,
        executionResult: ExecutionResult,
        config: LuaRedisRunConfiguration,
    ): RunContentDescriptor? =
        XDebuggerManager.getInstance(environment.project)
            .newSessionBuilder(object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess =
                    LuaRedisDebugProcess(session, executionResult, config)
            })
            .environment(environment)
            .startSession()
            .runContentDescriptor

    private fun notifyFailure(project: Project, failure: ExecutionException) {
        val message = failure.message ?: "Failed to start the Redis Lua debugger."
        NotificationGroupManager.getInstance()
            .getNotificationGroup("notification.group.lunar.debugger")
            .createNotification("Redis debug session failed", message, NotificationType.ERROR)
            .notify(project)
    }

    companion object {
        const val RUNNER_ID = "redis.ldb.debugrunner"

        private val log = logger<LuaRedisDebugRunner>()
    }
}
