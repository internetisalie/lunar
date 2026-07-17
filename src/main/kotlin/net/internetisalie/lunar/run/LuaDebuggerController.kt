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

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Responsible for interacting with the remote debugger client.
 *
 * Listens for and accepts connections on the standard Lua debug port (8172). After accepting a
 * connection, sends breakpoints and begins execution. Structured-concurrency rewrite (MAINT-22):
 * request/response correlation now lives in [LuaDebugConnection] via `CompletableDeferred`; this
 * controller owns a session-scoped [scope] (cancelled in [close]) and drives the connection through
 * suspend commands.
 */
class LuaDebuggerController(
    private val session: XDebugSession,
    private val scope: CoroutineScope,
) {
    private var serverSocket: ServerSocket? = null
    private var clientAddress: InetAddress? = null
    private var connection: LuaDebugConnection? = null
    private var serverPort: Int = LuaRunConfigurationOptions.DEFAULT_DEBUG_PORT
    private var console: ConsoleView? = null
    var isReady: Boolean = false
        private set

    private val myBreakpoints2Pos: ConcurrentHashMap<XBreakpoint<*>, LuaPosition> = ConcurrentHashMap()
    private val myPos2Breakpoints: ConcurrentHashMap<LuaPosition, XBreakpoint<*>> = ConcurrentHashMap()

    fun breakpointAt(pos: LuaPosition): XBreakpoint<*>? = myPos2Breakpoints[pos]

    private var baseDir: String
    private var workingDir: File

    init {
        session.setPauseActionSupported(false)

        serverPort = (this.session.runProfile as? LuaRunConfiguration)?.debugPort
            ?: LuaRunConfigurationOptions.DEFAULT_DEBUG_PORT

        val workingDirectory: String = listOfNotNull(
            (this.session.runProfile as? LuaRunConfiguration)?.workingDirectory,
            session.project.basePath,
            "",
        ).first()

        val baseDir = if (!workingDirectory.endsWith("/")) "$workingDirectory/" else workingDirectory
        this.baseDir = baseDir

        workingDir = File(baseDir)
    }

    fun printToConsole(text: String?, contentType: ConsoleViewContentType) {
        val console = this.console
        if (console == null) {
            log.error("Console not set")
            return
        }

        console.print(text + '\n', contentType)
    }

    /**
     * Bind the server socket, accept a client (bounded by [CONNECT_TIMEOUT_MS] via `soTimeout` — a blocking
     * `accept()` is not interruptible by coroutine cancellation, so the socket timeout is the real bound),
     * start the reader coroutine, and send the base dir. Suspends until the client is connected and ready.
     */
    @Throws(IOException::class)
    suspend fun connect() {
        log.info("Starting Debug Controller")
        val server = withContext(Dispatchers.IO) {
            ServerSocket(serverPort).apply { soTimeout = CONNECT_TIMEOUT_MS }
        }
        serverSocket = server

        val clientSocket = withContext(Dispatchers.IO) { server.accept() }
        clientAddress = clientSocket.inetAddress
        log.info("Client Connected $clientAddress")

        val conn = LuaDebugConnection(clientSocket, DebugObserver(), scope).also { it.start() }
        connection = conn

        printToConsole("Debugger connected at $clientAddress", ConsoleViewContentType.SYSTEM_OUTPUT)

        isReady = true
        setBaseDir()
    }

    fun terminate() {
        log.info("terminate")
        scope.launch {
            try {
                connection?.send(DebugCommand(DebugCommandKind.EXIT))
            } catch (e: Exception) {
                log.info("EXIT send failed: ${e.message}")
            }
        }.invokeOnCompletion { close() }
    }

    fun terminated() {
        log.info("terminated")
        close()
    }

    @Synchronized
    fun close() {
        log.info("close()")
        isReady = false
        serverSocket?.let {
            try {
                it.close()
            } catch (_: IOException) {
            }
            serverSocket = null
        }
        connection?.let {
            try {
                it.close()
            } catch (_: IOException) {
            }
            connection = null
        }
        // Cancel the session scope last: the reader coroutine unblocks via the socket close above,
        // then cancellation fails any outstanding CompletableDeferred (replacing the old promise sweep).
        scope.cancel()
    }

    fun setConsole(console: ConsoleView) {
        this.console = console
    }

    private suspend fun sendCommand(command: DebugCommand): String {
        val connection = this.connection ?: throw IOException("debugger connection closed")
        return connection.send(command)
    }

    // /////////////////////////// Remote Requests
    suspend fun stepInto() {
        sendCommand(DebugCommand(DebugCommandKind.STEP))
    }

    suspend fun stepOver() {
        sendCommand(DebugCommand(DebugCommandKind.OVER))
    }

    suspend fun stepOut() {
        sendCommand(DebugCommand(DebugCommandKind.OUT))
    }

    suspend fun resume() {
        sendCommand(DebugCommand(DebugCommandKind.RUN))
    }

    suspend fun setBaseDir() {
        sendCommand(DebugCommand(DebugCommandKind.BASEDIR, listOf(baseDir)))
    }

    suspend fun addBreakPoint(breakpoint: XBreakpoint<*>) {
        val sourcePosition = breakpoint.sourcePosition ?: return
        val pos = LuaPosition.createRemotePosition(sourcePosition, workingDir)

        myBreakpoints2Pos.put(breakpoint, pos)
        myPos2Breakpoints.put(pos, breakpoint)

        sendCommand(DebugCommand(DebugCommandKind.SETB, pos.args()))
    }

    suspend fun removeBreakPoint(breakpoint: XBreakpoint<*>) {
        val sourcePosition = breakpoint.sourcePosition ?: return
        val pos = LuaPosition.createRemotePosition(sourcePosition, workingDir)

        myBreakpoints2Pos.remove(breakpoint)
        myPos2Breakpoints.remove(pos)

        sendCommand(DebugCommand(DebugCommandKind.DELB, pos.args()))
    }

    suspend fun execute(statement: String): LuaDebugValue {
        val text = sendCommand(DebugCommand(DebugCommandKind.EXEC, listOf(statement)))
        return ApplicationManager.getApplication().runReadAction<LuaDebugValue> {
            val table = LuaDebugValueParser.parseChunk(session.project, text)

            // Re-parse each string value in the result to recover types from stringification
            val reparsedTable = LuaTable()
            for (value in table.indexed) {
                val reparsed = if (value.kind == LuaValueKind.String) {
                    LuaDebugValueParser.parseStringAsLuaValue(session.project, value.stringValue ?: "") ?: value
                } else {
                    value
                }
                reparsedTable.indexed.add(reparsed)
            }
            for ((key, value) in table.named) {
                val reparsed = if (value.kind == LuaValueKind.String) {
                    LuaDebugValueParser.parseStringAsLuaValue(session.project, value.stringValue ?: "") ?: value
                } else {
                    value
                }
                reparsedTable.named[key] = reparsed
            }

            // If the result is a single scalar value, return it directly instead of wrapping in table
            val value = if (reparsedTable.indexed.size == 1 && reparsedTable.named.isEmpty()) {
                reparsedTable.indexed[0]
            } else {
                LuaValue.newTable(reparsedTable)
            }
            LuaDebugValue(value, null, AllIcons.Nodes.Lambda)
        }
    }

    /** Bridge for [LuaDebuggerEvaluator] (XDebugger callback API): evaluate on [scope], report via [callback]. */
    fun launchEvaluate(statement: String, callback: XDebuggerEvaluator.XEvaluationCallback) {
        scope.launch {
            try {
                callback.evaluated(execute(statement))
            } catch (e: Exception) {
                log.info("evaluate failed: ${e.message}")
                callback.errorOccurred(e.message ?: "Evaluation error")
            }
        }
    }

    suspend fun variables(): LuaRemoteStack {
        val text = sendCommand(DebugCommand(DebugCommandKind.STACK))
        return ApplicationManager.getApplication().runReadAction<LuaRemoteStack> {
            LuaRemoteStack.create(session.project, text)
        }
    }

    inner class DebugObserver : LuaDebugObserver {
        override fun onPauseWatchpoint(pos: LuaPosition, watchIndex: Int) {
            log.info("watch $watchIndex at ${pos.path} line ${pos.line}")
            onPause(pos)
        }

        override fun onPauseBreakpoint(pos: LuaPosition) {
            log.info("break at ${pos.path} line ${pos.line}")
            onPause(pos)
        }

        private fun onPause(pos: LuaPosition) {
            val bp: XBreakpoint<*>? = breakpointAt(pos)

            scope.launch {
                val stack = variables()
                if (bp != null) {
                    // Breakpoint fired
                    val ctx = LuaSuspendContext(session.project, this@LuaDebuggerController, bp, stack)
                    session.breakpointReached(bp, null, ctx)
                } else {
                    // Watchpoint fired / Step completed
                    val sp: XSourcePosition? = pos.localPosition()
                    val ctx = LuaSuspendContext(session.project, this@LuaDebuggerController, sp, stack)
                    session.positionReached(ctx)
                }
            }
        }

        override fun onRunExecutionError(file: String) {
            log.error("Received execution error: $file")

            printToConsole("Execution Error: $file", ConsoleViewContentType.ERROR_OUTPUT)
            session.reportError("Execution Error: $file")
            session.stop()
        }

        override fun onDisconnected() {
            log.info("Disconnected")
            close()
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS: Int = 5_000

        val log = logger<LuaDebuggerController>()
    }
}
