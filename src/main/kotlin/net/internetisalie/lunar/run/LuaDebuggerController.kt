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
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.*

/**
 * Responsible for interacting with the remote debugger client.
 *
 * Listens for and accepts connections on the standard Lua
 * debug port (8172).  After accepting a connection, sends a
 * list of breakpoints and begins execution.
 *
 * Remote client is responsible for stopping at any set breakpoints,
 * and returning a call stack upon doing so.
 */
class LuaDebuggerController(
    private val session: XDebugSession,
) {
    private var serverSocket: ServerSocket? = null
    private var clientAddress: InetAddress? = null
    private var connection: LuaDebugConnection? = null
    private var serverPort: Int = 8172
    private var requests: MutableMap<DebugCommand, AsyncPromise<String>> = IdentityHashMap()
    private var console: ConsoleView? = null
    var isReady: Boolean = false
        private set

    var myBreakpoints2Pos: MutableMap<XBreakpoint<*>?, LuaPosition?> = HashMap<XBreakpoint<*>?, LuaPosition?>()
    var myPos2Breakpoints: MutableMap<LuaPosition?, XBreakpoint<*>?> = HashMap<LuaPosition?, XBreakpoint<*>?>()

    private var baseDir: String
    private var workingDir: File

    init {
        session.setPauseActionSupported(false)

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

    @Throws(IOException::class)
    fun waitForConnect() {
        try {
            log.info("Starting Debug Controller")
            this.serverSocket = ServerSocket(serverPort)
        } catch (e: IOException) {
            log.error("Failed to bind server socket on port $serverPort", e)
            throw e
        }

        var count = 0

        // Accept a connection
        while (connection == null) {
            try {
                log.info("Accepting Connection")
                val clientSocket = serverSocket!!.accept()
                clientAddress = clientSocket.inetAddress

                log.info("Client Connected $clientAddress")
                connection = LuaDebugConnection(clientSocket, DebugObserver())
                break
            } catch (e: InterruptedException) {
                log.warn("Interrupted while waiting for client connection", e)
            } catch (e: IOException) {
                log.error("Failed to accept client connection.", e)
                return
            }

            Thread.sleep(100)
            if (++count > 50) throw RuntimeException("timeout")
        }

        printToConsole("Debugger connected at $clientAddress", ConsoleViewContentType.SYSTEM_OUTPUT)

        // Run the connection in the background
        ApplicationManager.getApplication().executeOnPooledThread {
            connection?.run()
            log.info("Debug Controller terminated")
        }

        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            log.warn("Interrupted during post-connect wait", e)
        }

        this.isReady = true

        setBaseDir()
    }

    fun terminate() {
        log.info("terminate")
        queueRequest(DebugCommand(DebugCommandKind.EXIT)).then { close() }.onError { close() }
    }

    fun terminated() {
        log.info("terminated")
        close()
    }

    @Synchronized
    fun close() {
        log.info("close()")
        isReady = false
        if (serverSocket != null) {
            try {
                serverSocket!!.close()
            } catch (_: IOException) {
            }

            serverSocket = null
        }
        if (connection != null) {
            try {
                connection!!.close()
            } catch (_: IOException) {
            }

            connection = null
        }
        val remaining: List<AsyncPromise<String>>
        synchronized(requests) {
            remaining = requests.values.toList()
            requests.clear()
        }
        remaining.forEach { it.setError("debugger connection closed") }
    }

    fun setConsole(console: ConsoleView) {
        this.console = console
    }

    private fun queueRequest(command: DebugCommand): Promise<String> {
        log.info("Queuing command ${command.kind.name}")
        val connection = this.connection ?: return rejectedPromise("debugger connection closed")

        val promise = AsyncPromise<String>()
        synchronized(requests) {
            requests.put(command, promise)
        }

        connection.queue(command)

        return promise
    }

    private fun queueCommand(command: DebugCommand): Promise<Unit> {
        return queueRequest(command).then {}
    }

    /**/////////////////////////// */ // Remote Requests
    fun stepInto(): Promise<String> {
        return queueRequest(DebugCommand(DebugCommandKind.STEP))
    }

    fun stepOver(): Promise<String> {
        return queueRequest(DebugCommand(DebugCommandKind.OVER))
    }

    fun stepOut(): Promise<String> {
        return queueRequest(DebugCommand(DebugCommandKind.OUT))
    }

    fun resume(): Promise<String> {
        return queueRequest(DebugCommand(DebugCommandKind.RUN))
    }

    fun setBaseDir(): Promise<Unit> {
        return queueCommand(
            DebugCommand(
                DebugCommandKind.BASEDIR,
                listOf(baseDir),
            ),
        )
    }

    fun addBreakPoint(breakpoint: XBreakpoint<*>): Promise<Unit> {
        val sourcePosition = breakpoint.sourcePosition ?: return rejectedPromise("debugger connection closed")
        val pos = LuaPosition.createRemotePosition(sourcePosition, workingDir)

        myBreakpoints2Pos.put(breakpoint, pos)
        myPos2Breakpoints.put(pos, breakpoint)

        return queueCommand(DebugCommand(DebugCommandKind.SETB, pos.args()))
    }

    fun removeBreakPoint(breakpoint: XBreakpoint<*>): Promise<Unit> {
        val sourcePosition = breakpoint.sourcePosition ?: return rejectedPromise("debugger connection closed")
        val pos = LuaPosition.createRemotePosition(sourcePosition, workingDir)

        myBreakpoints2Pos.remove(breakpoint)
        myPos2Breakpoints.remove(pos)

        return queueCommand(DebugCommand(DebugCommandKind.DELB, pos.args()))
    }

    fun execute(statement: String): Promise<LuaDebugValue> {
        val command = DebugCommand(DebugCommandKind.EXEC, listOf(statement))
        return queueRequest(command)
            .then { text ->
                var luaDebugValue: LuaDebugValue? = null
                ApplicationManager.getApplication().runReadAction {
                    val table = LuaDebugValueParser.parseChunk(session.project, text)

                    // Re-parse each string value in the result to recover types from stringification
                    val reparsedTable = LuaTable()
                    for ((idx, value) in table.indexed.withIndex()) {
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
                    luaDebugValue = LuaDebugValue(value, null, AllIcons.Nodes.Lambda)
                }
                luaDebugValue!!
            }.onError { e -> log.error(e) }
    }

    fun variables(): Promise<LuaRemoteStack> {
        val command = DebugCommand(DebugCommandKind.STACK)
        return queueRequest(command)
            .then {
                var luaRemoteStack: LuaRemoteStack? = null
                ApplicationManager.getApplication().runReadAction {
                    luaRemoteStack = LuaRemoteStack.create(session.project, it)
                }
                luaRemoteStack!!
            }
            .onError { e -> log.error(e) }
    }


    inner class DebugObserver : LuaDebugObserver {
        override fun onCommandComplete(
            command: DebugCommand,
            status: DebuggerStatus,
            data: String,
        ) {
            log.info("Received response to $command: $data")

            val promise: AsyncPromise<String>
            synchronized(requests) {
                promise = requests.remove(command) ?: return
            }

            if (status.isError) {
                promise.setError(data)
            } else {
                promise.setResult(data)
            }
        }

        override fun onCommandCancelled(command: DebugCommand) {
            val promise: AsyncPromise<String>
            synchronized(requests) {
                promise = requests.remove(command) ?: return
            }
            promise.setError("command cancelled: ${command.kind.name}")
        }

        override fun onPauseWatchpoint(
            pos: LuaPosition,
            watchIndex: Int,
        ) {
            log.info("watch $watchIndex at ${pos.path} line ${pos.line}")
            onPause(pos)
        }

        override fun onPauseBreakpoint(pos: LuaPosition) {
            log.info("break at ${pos.path} line ${pos.line}")
            onPause(pos)
        }

        fun onPause(pos: LuaPosition) {
            val bp: XBreakpoint<*>? = myPos2Breakpoints.get(pos)

            variables().then { stack ->
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
            log.error("Received execution error in $file")

            TODO("Not yet implemented")
        }

        override fun onDisconnected() {
            log.info("Disconnected")
            close()
        }
    }

    companion object {
        val log = logger<LuaDebuggerController>()
    }
}
