package net.internetisalie.lunar.run

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext

enum class DebuggerStatus(val code: Int, val message: String) {
    OK(200, "200 OK"),
    Started(201, "201 Started"),
    PausedBreakpoint(202, "202 Paused"),
    PausedWatchpoint(203, "203 Paused"),
    Output(204, "204 Output"),
    BadRequest(400, "400 Bad Request"),
    ErrorInExecution(401, "401 Error in Execution"),
    ErrorInExpression(401, "401 Error in Expression");

    val isError: Boolean
        get() = code >= 400
}

enum class DebuggerResponseDataKind {
    None,
    Immediate,
    Extended,
}

enum class DebugCommandGroup {
    Config,
    Inspect,
    Run,
    Terminate,
}

enum class DebugCommandKind(
    val group: DebugCommandGroup,
    val responses: Map<DebuggerStatus, DebuggerResponseDataKind> = mapOf(
        DebuggerStatus.OK to DebuggerResponseDataKind.None,
        DebuggerStatus.BadRequest to DebuggerResponseDataKind.None,
    ),
    val minArgs: Int = 0,
    val maxArgs: Int = 0,
) {
    BASEDIR(
        group = DebugCommandGroup.Config,
        maxArgs = 1
    ),
    SETB(
        group = DebugCommandGroup.Config,
        minArgs = 2,
        maxArgs = 2
    ),
    DELB(
        group = DebugCommandGroup.Config,
        minArgs = 2,
        maxArgs = 2
    ),
    SETW(
        group = DebugCommandGroup.Config,
        responses = mapOf(
            DebuggerStatus.OK to DebuggerResponseDataKind.Immediate,
            DebuggerStatus.BadRequest to DebuggerResponseDataKind.None,
            DebuggerStatus.ErrorInExpression to DebuggerResponseDataKind.Extended,
        ),
        minArgs = 1,
        maxArgs = 1
    ),
    DELW(
        group = DebugCommandGroup.Config,
        minArgs = 1,
        maxArgs = 1
    ),

    STACK(
        group = DebugCommandGroup.Inspect,
        responses = mapOf(
            DebuggerStatus.OK to DebuggerResponseDataKind.Immediate,
            DebuggerStatus.ErrorInExecution to DebuggerResponseDataKind.Extended,
        ),
    ),
    EXEC(
        group = DebugCommandGroup.Inspect,
        responses = mapOf(
            DebuggerStatus.OK to DebuggerResponseDataKind.Extended,
            DebuggerStatus.BadRequest to DebuggerResponseDataKind.None,
            DebuggerStatus.ErrorInExpression to DebuggerResponseDataKind.Extended,
        ),
        minArgs = 1,
        maxArgs = 1,
    ),

    RUN(
        group = DebugCommandGroup.Run,
        responses = mapOf(
            DebuggerStatus.OK to DebuggerResponseDataKind.None,
        ),
    ),
    STEP(
        group = DebugCommandGroup.Run,
        responses = mapOf(
            DebuggerStatus.OK to DebuggerResponseDataKind.None,
        )
    ),
    OVER(
        group = DebugCommandGroup.Run,
        responses = mapOf(
            DebuggerStatus.OK to DebuggerResponseDataKind.None,
        )
    ),
    OUT(
        group = DebugCommandGroup.Run,
        responses = mapOf(
            DebuggerStatus.OK to DebuggerResponseDataKind.None,
        )
    ),

    EXIT(
        group = DebugCommandGroup.Terminate,
        responses = mapOf(
            DebuggerStatus.OK to DebuggerResponseDataKind.None,
        )
    ),
    DONE(
        group = DebugCommandGroup.Terminate,
        responses = mapOf(
            DebuggerStatus.OK to DebuggerResponseDataKind.None,
        )
    )
}

data class DebugCommand(
    val kind: DebugCommandKind,
    val args: List<String> = emptyList(),
) {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(kind.name.uppercase())
        for (arg: String in args) {
            sb.append(" ")
            sb.append(arg)
        }
        return sb.toString()
    }
}

/** Raised when the debuggee returns an error status ([DebuggerStatus.isError]) for a command. */
class DebuggerError(val status: DebuggerStatus, val data: String) :
    IOException("${status.message}: $data")

interface LuaDebugObserver {
    // Out-of-band events emitted while the debuggee is running.
    fun onPauseWatchpoint(pos: LuaPosition, watchIndex: Int)
    fun onPauseBreakpoint(pos: LuaPosition)
    fun onRunExecutionError(file: String)
    fun onDisconnected()
}

/**
 * DBGp transport over a single socket (MAINT-22).
 *
 * Structured-concurrency rewrite of the former `Thread.sleep(50)` poll loop + promise-map correlation:
 * a single **reader coroutine** ([readLoop]) owns the buffered [InputStream]; [send] publishes one
 * [CompletableDeferred] at a time under [writeMutex], which the reader completes. The protocol keeps at
 * most one command in flight and has a distinct **running** phase (after a [DebugCommandGroup.Run] command
 * the debuggee runs, then emits an out-of-band pause/error line) — that state machine is preserved exactly
 * in [handleLine], mirroring the branches of the old `receive()`.
 */
class LuaDebugConnection(
    private val socket: Socket,
    private val observer: LuaDebugObserver,
    private val scope: CoroutineScope,
) {
    private val input: InputStream = BufferedInputStream(socket.inputStream, 100 * 1024)
    private val writer: OutputStream = socket.outputStream

    private val writeMutex = Mutex()

    @Volatile
    private var pending: CompletableDeferred<String>? = null

    @Volatile
    private var pendingKind: DebugCommandKind? = null

    @Volatile
    private var running: Boolean = false

    private var readerJob: Job? = null

    /** Launch the reader coroutine on the (session-scoped) [scope]. */
    fun start() {
        readerJob = scope.launch(Dispatchers.IO) { readLoop() }
    }

    /**
     * Send [command] and suspend until the reader coroutine completes its response.
     * Only one command is in flight at a time (guarded by [writeMutex]).
     */
    suspend fun send(command: DebugCommand): String = writeMutex.withLock {
        if (socket.isClosed) throw IOException("debugger connection closed")
        log.info("Sending command ${command.kind.name}")

        val deferred = CompletableDeferred<String>()
        pending = deferred
        pendingKind = command.kind
        withContext(Dispatchers.IO) {
            DbgpFraming.writeLine(writer, command.toString())
        }
        deferred.await()
    }

    private suspend fun readLoop() {
        try {
            while (coroutineContext.isActive && !socket.isClosed) {
                val line = DbgpFraming.readLine(input) ?: break
                handleLine(line)
            }
        } catch (e: IOException) {
            log.info("readLoop closed: ${e.message}")
        } catch (e: Exception) {
            log.warn("readLoop unexpected exception: ${e.message}", e)
        } finally {
            log.info("readLoop exiting, connection closed")
            pending?.completeExceptionally(IOException("connection closed"))
            pending = null
            observer.onDisconnected()
        }
    }

    /** Parses one incoming line — reproduces the old `receive()` branching exactly. */
    private fun handleLine(line: String) {
        val status = DebuggerStatus.entries.firstOrNull { line.startsWith(it.message) }
            ?: throw IOException("unknown status in response: ${line.take(80)}")
        val data: String = line.removePrefix(status.message).removePrefix(" ").trimEnd('\n')

        val deferred = pending
        val kind = pendingKind
        val declared = kind?.responses ?: emptyMap()

        // Case A: response to the in-flight command.
        if (deferred != null && declared.containsKey(status)) {
            val payload = if (declared[status] == DebuggerResponseDataKind.Extended) {
                DbgpFraming.readExactly(input, data.toInt())
            } else {
                data
            }
            if (kind?.group == DebugCommandGroup.Run) running = true
            pending = null
            pendingKind = null
            if (status.isError) {
                deferred.completeExceptionally(DebuggerError(status, payload))
            } else {
                deferred.complete(payload)
            }
            return
        }

        // Case B: out-of-band event while the debuggee is running.
        if (running) {
            when (status) {
                DebuggerStatus.PausedBreakpoint -> {
                    running = false
                    val m = breakpointDataPattern.matcher(data)
                    if (m.matches()) {
                        val pos = LuaPosition(m.group(1), m.group(2).toInt())
                        log.info("breakpoint pause at ${pos.path}:${pos.line}")
                        observer.onPauseBreakpoint(pos)
                    } else {
                        log.warn("PausedBreakpoint data did not match pattern: '$data'")
                    }
                }

                DebuggerStatus.PausedWatchpoint -> {
                    running = false
                    val m = watchpointDataPattern.matcher(data)
                    if (m.matches()) {
                        val pos = LuaPosition(m.group(1), m.group(2).toInt())
                        val index = m.group(3).toInt()
                        log.info("watchpoint pause at ${pos.path}:${pos.line} index=$index")
                        observer.onPauseWatchpoint(pos, index)
                    } else {
                        log.warn("PausedWatchpoint data did not match pattern: '$data'")
                    }
                }

                DebuggerStatus.ErrorInExecution -> {
                    running = false
                    val extData = DbgpFraming.readExactly(input, data.toInt())
                    log.info("execution error: $extData")
                    observer.onRunExecutionError(extData)
                }

                else -> {
                    log.error("handleLine() unexpected status while running: ${status.message}")
                }
            }
            return
        }

        log.error("handleLine() unhandled response: status=$status data='${data.take(80)}' (running=$running)")
    }

    fun close() {
        try {
            input.close()
        } catch (_: IOException) {
        }

        try {
            writer.close()
        } catch (_: IOException) {
        }

        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        val breakpointDataPattern: Pattern = Pattern.compile("^(.+)\\s+(\\d+)$")
        val watchpointDataPattern: Pattern = Pattern.compile("^(.+)\\s+(\\d+)\\s+(\\d+)$")

        val log = logger<LuaDebugConnection>()
    }
}
