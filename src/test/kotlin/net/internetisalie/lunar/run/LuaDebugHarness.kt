package net.internetisalie.lunar.run

import java.io.File
import java.net.ServerSocket
import java.net.Socket

private val LUA_BIN = "/home/mini/bin/lua"
private val PROJECT_ROOT = File("/home/mini/Documents/src/lua/lunar")
private val DEBUG_LUA = File(PROJECT_ROOT, "src/main/lua/debug.lua")
private val MOBDEBUG_DIR = File(PROJECT_ROOT, "src/main/lua")

/**
 * Starts a Lua subprocess with mobdebug injected and accepts its debug connection.
 *
 * The returned [LuaHarness] holds the started [Process] and the accepted [LuaDebugConnection]
 * running on a background thread. Call [LuaHarness.close] when done.
 *
 * @param script    Path to the Lua script to run.
 * @param observer  Observer to receive debug events.
 */
fun startLuaDebugHarness(script: File, observer: LuaDebugObserver): LuaHarness {
    val serverSocket = ServerSocket(8172)

    val process = ProcessBuilder(LUA_BIN, script.absolutePath)
        .redirectErrorStream(true)
        .apply {
            environment()["LUA_INIT"] = "@${DEBUG_LUA.absolutePath}"
            environment()["LUNAR_DEBUGGER_PACKAGE"] = "mobdebug"
            // mobdebug is a directory module (init.lua), so expose both patterns
            environment()["LUNAR_LUA_PATH_TEMPLATE"] =
                "${MOBDEBUG_DIR.absolutePath}/?.lua;${MOBDEBUG_DIR.absolutePath}/?/init.lua"
        }
        .start()

    val clientSocket: Socket = try {
        serverSocket.soTimeout = 20_000
        serverSocket.accept()
    } finally {
        serverSocket.close()
    }

    val connection = LuaDebugConnection(clientSocket, observer)
    val connectionThread = Thread(connection::run, "lua-debug-connection").apply {
        isDaemon = true
        start()
    }

    return LuaHarness(process, clientSocket, connection, connectionThread)
}

class LuaHarness(
    val process: Process,
    private val socket: Socket,
    val connection: LuaDebugConnection,
    private val connectionThread: Thread,
) : AutoCloseable {
    override fun close() {
        connection.close()
        connectionThread.join(2_000)
        process.destroyForcibly()
    }
}
