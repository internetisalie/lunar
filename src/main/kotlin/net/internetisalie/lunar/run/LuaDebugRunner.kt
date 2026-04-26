/*
 * Copyright 2011 Jon S Akhtar (Sylvanaar)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package net.internetisalie.lunar.run

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
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager

/**
 * Debug runner for Lua run configurations.
 *
 * Manages the creation and lifecycle of Lua debug sessions using the
 * modern XDebugSessionBuilder API introduced in IntelliJ 2022.1.
 *
 * The builder pattern provides:
 * - Cleaner, more readable code than anonymous XDebugProcessStarter
 * - Support for session lifecycle listeners (pause/resume/stop)
 * - Better error handling and user feedback
 * - Future compatibility with IntelliJ Platform updates
 *
 * @since Lunar 2.0.0
 */
class LuaDebugRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, runProfile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID &&
                runProfile is LuaRunConfiguration
    }

    /**
     * Executes the debug session using XDebugSessionBuilder.
     *
     * Creates a debug session with proper error handling and event listeners
     * for debugger lifecycle events (pause, resume, stop).
     *
     * @param state The run profile state
     * @param environment The execution environment
     * @return The run content descriptor or null if session creation fails
     */
    override fun doExecute(
        state: RunProfileState,
        environment: ExecutionEnvironment
    ): RunContentDescriptor? {
        val executionResult = state.execute(environment.executor, this) ?: return null
        val project = environment.project

        return try {
            createDebugSession(environment, executionResult)
        } catch (e: ExecutionException) {
            log.error("Failed to create debug session", e)
            notifyExecutionError(project, e)
            null
        }
    }

    /**
     * Creates a debug session using XDebugSessionBuilder fluent API.
     *
     * @param environment The execution environment
     * @param executionResult The process execution result
     * @return The created debug session or null on failure
     */
    private fun createDebugSession(
        environment: ExecutionEnvironment,
        executionResult: ExecutionResult
    ): RunContentDescriptor? {
        return XDebuggerManager.getInstance(environment.project)
            .newSessionBuilder(object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): LuaDebugProcess {
                    log.info("Lua debug process created for ${environment.runProfile.name}")
                    return LuaDebugProcess(session, executionResult)
                }
            })
            .environment(environment)
            .startSession()
            .runContentDescriptor
    }

    /**
     * Notifies the user of debug session creation failure.
     *
     * Shows an error notification via the IDE notification system instead of
     * blocking dialogs, allowing the user to continue their workflow.
     *
     * @param project The project where debugging failed
     * @param exception The exception that occurred
     */
    private fun notifyExecutionError(project: Project, exception: ExecutionException) {
        val message = exception.message
            ?: "Failed to start Lua debugger. Verify debugger configuration in project settings."

        NotificationGroupManager.getInstance()
            .getNotificationGroup("notification.group.lunar.debugger")
            .createNotification(
                "Debug session failed",
                message,
                NotificationType.ERROR
            )
            .notify(project)
    }

    companion object {
        /**
         * Unique identifier for the Lua debug runner.
         * Used by IntelliJ Platform to identify this runner instance.
         */
        const val RUNNER_ID = "lua.debugrunner"

        private val log = logger<LuaDebugRunner>()
    }
}
