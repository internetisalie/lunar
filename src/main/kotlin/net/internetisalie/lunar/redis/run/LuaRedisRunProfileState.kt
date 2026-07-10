package net.internetisalie.lunar.redis.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.util.coroutines.childScope
import net.internetisalie.lunar.redis.connection.LuaRedisCredentialStore
import net.internetisalie.lunar.redis.connection.LuaRedisProvisioning
import net.internetisalie.lunar.redis.connection.LuaRedisServerConnection
import net.internetisalie.lunar.redis.connection.LuaRedisServerLauncher
import net.internetisalie.lunar.redis.console.LuaRedisErrorLinkFilter
import net.internetisalie.lunar.redis.console.RespReplyTreeConsole
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespEndpoint
import net.internetisalie.lunar.redis.resp.RespException
import net.internetisalie.lunar.redis.resp.RespValue
import net.internetisalie.lunar.util.LunarCoroutineScopeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

private val log = logger<LuaRedisRunProfileState>()

/**
 * A no-op [ProcessHandler] whose termination cancels the run's session scope (design §2.11).
 *
 * The run "process" is the coroutine orchestration, not an OS process; stopping the run (or the run
 * completing) cancels [sessionScope], disposing the client and stopping any session-launched server
 * (risks-and-gaps Risk 1.3). Never blocks the EDT — all real work runs on [sessionScope].
 */
private class LuaRedisSessionProcessHandler(private val sessionScope: CoroutineScope) : ProcessHandler() {
    override fun destroyProcessImpl() {
        sessionScope.cancel()
        notifyProcessTerminated(0)
    }

    override fun detachProcessImpl() {
        sessionScope.cancel()
        notifyProcessDetached()
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? = null

    fun finish() {
        if (!isProcessTerminated) notifyProcessTerminated(0)
    }
}

/**
 * Orchestrates a "Redis Script" run: launch → connect → execute → console (design §2.11, §5).
 *
 * [execute] returns immediately on the EDT with a [DefaultExecutionResult] wrapping the
 * [RespReplyTreeConsole]; the network work runs on a session `childScope` derived from
 * [LunarCoroutineScopeService] and cancelled on teardown (engineering-contract §2 COROUTINE
 * CONVENTIONS). PSI/VFS reads use suspend `readAction`; console updates marshal via
 * `withContext(Dispatchers.EDT)`. Decomposed into ≤30-line helpers (engineering-contract §3).
 */
class LuaRedisRunProfileState(
    private val config: LuaRedisRunConfiguration,
    private val environment: ExecutionEnvironment,
) : RunProfileState {

    private val project: Project get() = environment.project

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val console = RespReplyTreeConsole(project)
        bindErrorLinkFilter(console)

        val sessionScope = LunarCoroutineScopeService.getInstance(project).scope.childScope("LuaRedisRunSession")
        val processHandler = LuaRedisSessionProcessHandler(sessionScope)
        disposeConsoleOnTermination(processHandler, console)
        processHandler.startNotify()

        sessionScope.launch {
            runSession(console, processHandler)
        }
        return DefaultExecutionResult(console, processHandler)
    }

    private fun bindErrorLinkFilter(console: RespReplyTreeConsole) {
        val scriptUrl = scriptFileUrl() ?: return
        console.addFilter(LuaRedisErrorLinkFilter(project, scriptUrl))
    }

    private fun disposeConsoleOnTermination(processHandler: ProcessHandler, console: RespReplyTreeConsole) {
        processHandler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                Disposer.dispose(console)
            }
        })
    }

    private suspend fun runSession(console: RespReplyTreeConsole, processHandler: LuaRedisSessionProcessHandler) {
        var launcherStop: (() -> Unit)? = null
        var client: RespClient? = null
        try {
            val connection = resolveConnection()
            val launched = launchIfNeeded(connection)
            launcherStop = launched?.stop
            client = openClient(connection, launched?.host, launched?.port)
            val reply = executeScript(client, connection)
            showReply(console, reply)
        } catch (failure: Throwable) {
            reportFailure(console, failure)
        } finally {
            client?.dispose()
            runCatching { launcherStop?.invoke() }
            withContext(Dispatchers.EDT) { processHandler.finish() }
        }
    }

    private fun resolveConnection(): LuaRedisServerConnection =
        config.connection ?: throw ExecutionException("No Redis connection selected")

    private suspend fun launchIfNeeded(connection: LuaRedisServerConnection): net.internetisalie.lunar.redis.connection.LaunchedServer? {
        val provisioning = connection.provisioning
        if (provisioning is LuaRedisProvisioning.Remote) return null
        return LuaRedisServerLauncher(project).launch(provisioning)
    }

    private suspend fun openClient(connection: LuaRedisServerConnection, host: String?, port: Int?): RespClient {
        val password = LuaRedisCredentialStore.getPassword(connection.id)
        val endpoint = endpointFor(connection, host, port, password)
        return RespClient.open(endpoint)
    }

    private fun endpointFor(connection: LuaRedisServerConnection, host: String?, port: Int?, password: String?): RespEndpoint {
        val base = connection.toEndpoint(password)
        if (host == null || port == null) return base
        return base.copy(host = host, port = port)
    }

    private suspend fun executeScript(client: RespClient, connection: LuaRedisServerConnection): RespValue {
        val scriptBody = readScriptBody()
        val context = LuaRedisExecContext(
            connectionId = connection.id,
            execMode = config.execMode,
            readOnly = config.readOnly,
            keys = config.keys,
            argv = config.argv,
        )
        return LuaRedisScriptExecutor().execute(client, context, scriptBody)
    }

    private suspend fun readScriptBody(): String {
        val url = scriptFileUrl() ?: throw ExecutionException("Script path is not defined")
        return readAction {
            val file = VirtualFileManager.getInstance().findFileByUrl(url)
                ?: throw ExecutionException("Script file not found: $url")
            String(file.contentsToByteArray(), Charsets.UTF_8)
        }
    }

    private suspend fun showReply(console: RespReplyTreeConsole, reply: RespValue) {
        withContext(Dispatchers.EDT) { console.showReply(reply) }
    }

    private suspend fun reportFailure(console: RespReplyTreeConsole, failure: Throwable) {
        val message = when (failure) {
            is RespException, is ExecutionException -> failure.message ?: failure.toString()
            else -> failure.message ?: failure.toString()
        }
        log.info("Redis run failed: $message")
        withContext(Dispatchers.EDT) { console.showError(RespValue.Error("ERROR", message)) }
    }

    private fun scriptFileUrl(): String? {
        val path = config.scriptPath?.takeIf { it.isNotBlank() } ?: return null
        return VirtualFileManager.constructUrl("file", File(path).absolutePath)
    }
}
