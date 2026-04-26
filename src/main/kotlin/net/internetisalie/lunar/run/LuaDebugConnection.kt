package net.internetisalie.lunar.run

import com.intellij.openapi.diagnostic.logger
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.util.regex.Pattern

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

interface LuaDebugObserver {
    fun onCommandComplete(command: DebugCommand, status: DebuggerStatus, data: String)
    fun onCommandCancelled(command: DebugCommand)

    // Out-of-band events when running
    fun onPauseWatchpoint(pos: LuaPosition, watchIndex: Int)
    fun onPauseBreakpoint(pos: LuaPosition)
    fun onRunExecutionError(file: String)
    fun onDisconnected()
}

class LuaDebugConnection(
    private val socket: Socket,
    private val observer: LuaDebugObserver,
) {
    private val reader: BufferedReader = InputStreamReader(socket.inputStream).buffered(100 * 1024)
    private val writer: OutputStream = socket.outputStream

    private var current: DebugCommand? = null
    private var running: Boolean = false
    private var started: Boolean = false
    private val commands: ArrayDeque<DebugCommand> = ArrayDeque()

    private val charset = charset("UTF8")

    fun queue(command: DebugCommand) {
        log.info("Queueing command ${command.kind.name}")

        // Immediately cancel if we are done
        if (socket.isClosed) {
            observer.onCommandCancelled(command)
            return
        }

        // exclusive access to commands
        synchronized(this) {
            commands.add(command)
        }
        send()
    }

    fun run() {
        // exclusive access to started
        synchronized(this) {
            if (started) return
            started = true
        }

        try {
            while (true) {
                // Quit if we are done
                if (socket.isClosed) {
                    log.info("run() loop exit: socket closed")
                    break
                }

                val hasCurrent = current != null
                val isRunning = running
                val readerReady = reader.ready()

                // Send a queued command if we are open
                if (!hasCurrent && !isRunning) {
                    send()
                }

                // Receive a response if we are expecting one and it is available
                if ((current != null || running) && readerReady) {
                    receive()
                } else if (!readerReady && (current != null || running)) {
                    try {
                        Thread.sleep(50)
                    } catch (_: InterruptedException) {
                        // ignore
                    }
                } else {
                    // snooze
                    try {
                        Thread.sleep(50)
                    } catch (_: InterruptedException) {
                        // ignore
                    }
                }
            }
        } catch (e: IOException) {
            log.warn("run() IO exception: ${e.message}", e)
        } catch (e: Exception) {
            log.warn("run() unexpected exception: ${e.message}", e)
        } finally {
            log.info("run() exiting, connection closed")
            synchronized(this) {
                started = false
            }
        }

        synchronized(this) {
            while (commands.isNotEmpty()) {
                observer.onCommandCancelled(commands.removeFirst())
            }
        }

        observer.onDisconnected()
    }

    private fun send() {
        // Pre-check conditions out of synchronization
        if (socket.isClosed) return
        if (current != null || running) return

        // exclusive access to commands, writer
        synchronized(this) {
            if (current != null || running) return
            val command = commands.removeFirstOrNull() ?: return
            current = command
            writer.write("$command\n".toByteArray(charset))
        }
    }

    private fun receive() {
        // Parse the base response
        val result = reader.readLine() ?: throw IOException("connection closed — readLine() returned null")
        val status = DebuggerStatus.entries.firstOrNull { result.startsWith(it.message) }
            ?: throw IOException("receive() unknown status in response: ${result.take(80)}")
        val data: String = result.removePrefix(status.message).removePrefix(" ").trimEnd('\n')

        // Parse the extended response if required
        val currentResponses = current?.kind?.responses ?: emptyMap()
        if (currentResponses.containsKey(status)) {
            if (currentResponses[status] == DebuggerResponseDataKind.Extended) {
                val length = data.toInt()
                val extData = reader.readExactly(length)
                observer.onCommandComplete(current!!, status, extData)
            } else {
                observer.onCommandComplete(current!!, status, data)
            }
            if (current?.kind?.group == DebugCommandGroup.Run) {
                running = true
            }
            current = null
            return
        }

        if (running) {
            when (status) {
                DebuggerStatus.PausedBreakpoint -> {
                    running = false

                    val m = breakpointDataPattern.matcher(data)
                    if (m.matches()) {
                        val file = m.group(1)
                        val line = m.group(2).toInt()
                        val pos = LuaPosition(file, line)
                        log.info("breakpoint pause at $file:$line")
                        observer.onPauseBreakpoint(pos)
                    } else {
                        log.warn("PausedBreakpoint data did not match pattern: '$data'")
                    }
                }

                DebuggerStatus.PausedWatchpoint -> {
                    running = false

                    val m = watchpointDataPattern.matcher(data)
                    if (m.matches()) {
                        val file = m.group(1)
                        val line = m.group(2).toInt()
                        val pos = LuaPosition(file, line)
                        val index = m.group(3).toInt()
                        log.info("watchpoint pause at $file:$line index=$index")
                        observer.onPauseWatchpoint(pos, index)
                    } else {
                        log.warn("PausedWatchpoint data did not match pattern: '$data'")
                    }
                }

                DebuggerStatus.ErrorInExecution -> {
                    running = false

                    val extData = reader.readExactly(data.toInt())
                    log.info("execution error: $extData")
                    observer.onRunExecutionError(extData)
                }

                else -> {
                    log.error("receive() unexpected status while running: ${status.message}")
                }
            }
        } else {
            log.error("receive() unhandled response: status=$status data='${data.take(80)}' (current=${current?.kind}, running=$running)")
        }
    }

    fun close() {
        try {
            reader.close()
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

    /** Reads exactly [length] characters from this reader without closing it. */
    private fun BufferedReader.readExactly(length: Int): String {
        val buf = CharArray(length)
        var offset = 0
        while (offset < length) {
            val n = read(buf, offset, length - offset)
            if (n == -1) throw IOException("connection closed after $offset of $length chars")
            offset += n
        }
        return String(buf)
    }

    companion object {
        val breakpointDataPattern: Pattern = Pattern.compile("^(.+)\\s+(\\d+)$")
        val watchpointDataPattern: Pattern = Pattern.compile("^(.+)\\s+(\\d+)\\s+(\\d+)$")

        val log = logger<LuaDebugConnection>()
    }

}
