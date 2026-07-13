package net.internetisalie.lunar.redis.debug

import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.internetisalie.lunar.redis.connection.LaunchedServer
import net.internetisalie.lunar.redis.connection.LuaRedisCredentialStore
import net.internetisalie.lunar.redis.connection.LuaRedisProvisioning
import net.internetisalie.lunar.redis.connection.LuaRedisServerConnection
import net.internetisalie.lunar.redis.connection.LuaRedisServerLauncher
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespException
import net.internetisalie.lunar.redis.resp.RespValue
import net.internetisalie.lunar.redis.run.LuaRedisRunConfiguration
import java.io.File

/**
 * Drives one live LDB debug session (design §2.3, §3.5, §3.6).
 *
 * Owns the session lifecycle — handshake + `EVAL`, then translating XDebugger actions into
 * [LdbCommand]s, feeding replies to the [LdbSessionMachine], and raising XDebugger
 * pause/resume/error events. The REDIS-02 analogue of `run/LuaDebuggerController`, but over the
 * REDIS-01 `RespClient` transport (no shared DBGp code, epic RISK-R09).
 *
 * All I/O runs on [scope] (a `LunarCoroutineScopeService` childScope, cancelled on [teardown]) — never
 * the EDT. [transportOpener] is a seam: production resolves the connection, launches a local server if
 * needed, opens a `RespClient`, and wraps it in [LuaLdbTransport]; unit tests supply a scripted [LdbIo]
 * (contract §5). Implements the Phase-2 [LuaLdbEvalHost] / [LuaLdbBreakpointRegistrar] seams.
 */
class LuaLdbController private constructor(
    private val session: XDebugSession,
    private val scope: CoroutineScope,
    private val config: LuaRedisRunConfiguration,
    private val transportOpener: (suspend () -> LdbIo)?,
) : LuaLdbEvalHost, LuaLdbBreakpointRegistrar {

    constructor(
        session: XDebugSession,
        scope: CoroutineScope,
        config: LuaRedisRunConfiguration,
    ) : this(session, scope, config, transportOpener = null)

    private val machine = LdbSessionMachine()
    private var transport: LdbIo? = null
    private var launchedStop: (() -> Unit)? = null
    private val pendingBreaks = mutableListOf<XBreakpoint<*>>()
    private val lineToBreakpoint = mutableMapOf<Int, XBreakpoint<*>>()

    init {
        session.setPauseActionSupported(false)
    }

    val isArmed: Boolean get() = machine.state != LdbState.HANDSHAKE && machine.state != LdbState.TERMINATED

    /** Connect, arm, drain breakpoints, EVAL, and raise the first pause (design §3.5). */
    suspend fun connect() {
        try {
            val io = openTransport()
            transport = io
            armSession(io)
            drainBreakpoints(io)
            runScript(io)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            reportFailure(failure)
        }
    }

    private suspend fun openTransport(): LdbIo = transportOpener?.invoke() ?: openLiveTransport()

    /** The production transport open (design §3.5 steps 1–4): resolve → launch-local → open → wrap. */
    private suspend fun openLiveTransport(): LdbIo {
        val connection = config.connection ?: throw ExecutionException("No Redis connection selected")
        val launched = launchIfNeeded(connection)
        launchedStop = launched?.stop
        val client = openClient(connection, launched)
        return LuaLdbTransport(client)
    }

    private suspend fun launchIfNeeded(connection: LuaRedisServerConnection): LaunchedServer? {
        val provisioning = connection.provisioning
        if (provisioning is LuaRedisProvisioning.Remote) return null
        return LuaRedisServerLauncher(session.project).launch(provisioning)
    }

    private suspend fun openClient(connection: LuaRedisServerConnection, launched: LaunchedServer?): RespClient {
        val password = LuaRedisCredentialStore.getPassword(connection.id)
        val base = connection.toEndpoint(password)
        val endpoint = if (launched == null) base else base.copy(host = launched.host, port = launched.port)
        return RespClient.open(endpoint)
    }

    private suspend fun armSession(io: LdbIo) {
        val reply = io.enterDebug(config.debugMode)
        if (reply is RespValue.Error) {
            throw ExecutionException(debugRefusedMessage(reply))
        }
        machine.onEvent(LdbEvent.Ack)
    }

    private suspend fun drainBreakpoints(io: LdbIo) {
        val pending = synchronized(pendingBreaks) { pendingBreaks.toList().also { pendingBreaks.clear() } }
        pending.forEach { breakpoint -> sendBreak(io, breakpoint) }
    }

    private suspend fun runScript(io: LdbIo) {
        val scriptBody = readScriptBody()
        val reply = io.eval(scriptBody, config.keys, config.argv)
        handleReply(reply)
    }

    override fun addBreakpoint(breakpoint: XBreakpoint<*>) {
        val io = transport
        if (io == null || !isArmed) {
            synchronized(pendingBreaks) { pendingBreaks.add(breakpoint) }
            return
        }
        scope.launch { commandGuard { sendBreak(io, breakpoint) } }
    }

    override fun removeBreakpoint(breakpoint: XBreakpoint<*>) {
        val io = transport ?: return
        val line = serverLineOf(breakpoint) ?: return
        scope.launch {
            commandGuard {
                lineToBreakpoint.remove(line)
                sendGuarded(io, LdbCommand.RemoveBreak(line))
            }
        }
    }

    private suspend fun sendBreak(io: LdbIo, breakpoint: XBreakpoint<*>) {
        val line = serverLineOf(breakpoint) ?: return
        lineToBreakpoint[line] = breakpoint
        sendGuarded(io, LdbCommand.Break(line))
    }

    /** Step Into. */
    suspend fun step() = resumeWith(LdbCommand.Step)

    /** Step Over. */
    suspend fun next() = resumeWith(LdbCommand.Next)

    /** Resume. */
    suspend fun continueRun() = resumeWith(LdbCommand.Continue)

    private suspend fun resumeWith(command: LdbCommand) {
        val io = transport ?: return
        commandGuard {
            val reply = sendGuarded(io, command) ?: return@commandGuard
            handleReply(reply)
        }
    }

    /** Abort the session and tear down (design §3.6). */
    suspend fun abort() {
        val io = transport
        if (io != null && machine.state != LdbState.TERMINATED) {
            runCatching { sendGuarded(io, LdbCommand.Abort) }
        }
        machine.onEvent(LdbEvent.SessionEnded(EndReason.ABORTED))
        teardown()
    }

    /** Read all frame locals via `print` (design §3.4). */
    suspend fun readLocals(): List<LuaLdbLocal> {
        val io = transport ?: return emptyList()
        val reply = sendGuarded(io, LdbCommand.Print(null)) ?: return emptyList()
        return LdbPrintParser.parseLocals(reply)
    }

    /** Run a Redis command in the paused session (design §2.7). */
    suspend fun redisCommand(args: List<String>): RespValue {
        val io = transport ?: return RespValue.Error("ERROR", "no active debug session")
        return sendGuarded(io, LdbCommand.RedisCmd(args)) ?: RespValue.Error("ERROR", "command rejected")
    }

    override fun launchEvaluate(expression: String, callback: XDebuggerEvaluator.XEvaluationCallback) {
        scope.launch { evaluate(expression, callback) }
    }

    /** Test seam: run [evaluate] inline (deterministic) instead of dispatching onto [scope]. */
    internal suspend fun evaluateForTest(expression: String, callback: XDebuggerEvaluator.XEvaluationCallback) =
        evaluate(expression, callback)

    private suspend fun evaluate(expression: String, callback: XDebuggerEvaluator.XEvaluationCallback) {
        val io = transport ?: run { callback.errorOccurred("No active debug session"); return }
        val reply = sendGuarded(io, LdbCommand.Eval(expression))
            ?: run { callback.errorOccurred("Cannot evaluate: session not paused"); return }
        val event = LdbReplyParser.parse(reply)
        if (event is LdbEvent.Error) {
            callback.errorOccurred(event.message)
            return
        }
        callback.evaluated(LuaLdbValue("result", LdbPrintParser.parseValue(reply)))
    }

    private suspend fun handleReply(reply: RespValue) {
        val event = LdbReplyParser.parse(reply)
        machine.onEvent(event)
        when (event) {
            is LdbEvent.Stop -> raisePause(event)
            is LdbEvent.Error -> reportError(event.message)
            is LdbEvent.SessionEnded -> endSession(event.reason)
            else -> Unit
        }
    }

    private suspend fun raisePause(stop: LdbEvent.Stop) {
        val position = readAction { positionFor(stop.serverLine) }
        val locals = readLocals()
        val breakpoint = lineToBreakpoint[stop.serverLine]
        val context = LuaLdbSuspendContext(position, this, locals)
        if (breakpoint != null) {
            session.breakpointReached(breakpoint, null, context)
        } else {
            session.positionReached(context)
        }
    }

    private fun endSession(reason: EndReason) {
        if (reason == EndReason.FORK_TIMEOUT) {
            session.reportMessage(
                "Redis debug session ended by the server (forked-session timeout).",
                MessageType.INFO,
            )
        }
        teardown()
    }

    private fun reportError(message: String) {
        session.reportError(message)
        teardown()
    }

    private fun reportFailure(failure: Throwable) {
        val message = failure.message ?: failure.toString()
        log.info("Redis debug session failed: $message")
        if (failure is RespException) notifyConnectionLoss(message)
        session.reportError(message)
        teardown()
    }

    private fun notifyConnectionLoss(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("notification.group.lunar.debugger")
            .createNotification("Redis debug session error", message, NotificationType.ERROR)
            .notify(session.project)
    }

    private fun teardown() {
        if (machine.state != LdbState.TERMINATED) machine.onEvent(LdbEvent.SessionEnded(EndReason.ENDED))
        (transport as? LuaLdbTransport)?.let { runCatching { it.dispose() } }
        runCatching { launchedStop?.invoke() }
        launchedStop = null
        transport = null
        scope.cancel()
    }

    private suspend fun sendGuarded(io: LdbIo, command: LdbCommand): RespValue? {
        if (!machine.onCommandSent(command)) {
            log.info("LDB command $command rejected in state ${machine.state}")
            return null
        }
        return io.send(command)
    }

    private suspend fun commandGuard(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            reportFailure(failure)
        }
    }

    private fun serverLineOf(breakpoint: XBreakpoint<*>): Int? =
        breakpoint.sourcePosition?.line?.plus(1)

    private fun positionFor(serverLine: Int): XSourcePosition? {
        val url = config.scriptPath?.takeIf { it.isNotBlank() }
            ?.let { VirtualFileManager.constructUrl("file", File(it).absolutePath) } ?: return null
        val file = VirtualFileManager.getInstance().findFileByUrl(url) ?: return null
        return XDebuggerUtil.getInstance().createPosition(file, serverLine - 1)
    }

    private suspend fun readScriptBody(): String {
        val path = config.scriptPath?.takeIf { it.isNotBlank() }
            ?: throw ExecutionException("Script path is not defined")
        val url = VirtualFileManager.constructUrl("file", File(path).absolutePath)
        return readAction {
            val file = VirtualFileManager.getInstance().findFileByUrl(url)
                ?: throw ExecutionException("Script file not found: $url")
            String(file.contentsToByteArray(), Charsets.UTF_8)
        }
    }

    private fun debugRefusedMessage(error: RespValue.Error): String =
        "This server does not permit script debugging (${error.klass} ${error.message}). " +
            "Debug against a local server instead."

    companion object {
        private val log = logger<LuaLdbController>()

        /** Test seam: build a controller driven by a scripted [transportOpener] (no live socket). */
        internal fun forTest(
            session: XDebugSession,
            scope: CoroutineScope,
            config: LuaRedisRunConfiguration,
            transportOpener: suspend () -> LdbIo,
        ): LuaLdbController = LuaLdbController(session, scope, config, transportOpener)
    }
}
