package net.internetisalie.lunar.run

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.net.ServerSocket
import java.net.Socket

// Resolve from the environment so the harness is portable (CI/remote builders), not pinned to one
// machine: the project root is the gradle working dir; the interpreter is ~/bin/lua.
private val LUA_BIN = File(System.getProperty("user.home"), "bin/lua").absolutePath
private val PROJECT_ROOT = File(System.getProperty("user.dir"))
private val DEBUG_LUA = File(PROJECT_ROOT, "src/main/lua/debug.lua")
private val MOBDEBUG_DIR = File(PROJECT_ROOT, "src/main/lua")

/**
 * Starts a Lua subprocess with mobdebug injected and accepts its debug connection.
 *
 * The returned [LuaHarness] holds the started [Process] and the accepted [LuaDebugConnection]
 * whose reader coroutine runs on the harness's own [CoroutineScope]. Call [LuaHarness.close] when done.
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

    val scope = CoroutineScope(SupervisorJob())
    val connection = LuaDebugConnection(clientSocket, observer, scope).also { it.start() }

    return LuaHarness(process, clientSocket, connection, scope)
}

class LuaHarness(
    val process: Process,
    private val socket: Socket,
    val connection: LuaDebugConnection,
    private val scope: CoroutineScope,
) : AutoCloseable {
    override fun close() {
        connection.close()
        scope.cancel()
        process.destroyForcibly()
    }
}
