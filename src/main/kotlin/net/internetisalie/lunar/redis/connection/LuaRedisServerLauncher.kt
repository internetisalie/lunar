package net.internetisalie.lunar.redis.connection

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.net.NetUtils
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver

private val log = logger<LuaRedisServerLauncher>()

/**
 * A live server instance started by [LuaRedisServerLauncher]; holds the host/port the
 * [net.internetisalie.lunar.redis.resp.RespClient] connects to, and an idempotent [stop] callback
 * that terminates the backing process/container (design §2.12, §3.9).
 */
class LaunchedServer(
    val host: String,
    val port: Int,
    val stop: () -> Unit,
)

/**
 * Seams injected for unit testing — overriding binary resolution and port allocation lets tests
 * assert on built [GeneralCommandLine]s without spawning real processes (TC-LAUNCH-1..3, design §3.9).
 *
 * Production code passes the defaults via [LuaRedisServerLauncher]'s primary constructor.
 */
internal class LaunchSeams(
    val resolveToolPath: (project: Project, toolKindId: String) -> String?,
    val resolveDockerPath: () -> String?,
    val allocatePort: () -> Int,
)

private fun defaultSeams(): LaunchSeams = LaunchSeams(
    resolveToolPath = { project, kindId ->
        LuaToolResolver.getInstance().resolve(project, kindId)?.path
    },
    resolveDockerPath = {
        PathEnvironmentVariableUtil.findInPath("docker")?.absolutePath
    },
    allocatePort = { NetUtils.findAvailableSocketPort() },
)

/**
 * Starts/stops a session-scoped Redis/Valkey server for [LuaRedisProvisioning.LocalBinary] or
 * [LuaRedisProvisioning.Docker] provisioning (design §2.12, §3.9).
 *
 * [LuaRedisProvisioning.Remote] is handled by the caller
 * ([net.internetisalie.lunar.redis.run.LuaRedisRunProfileState]) which uses the connection's own
 * host/port directly — the launcher is only invoked for "launch local" variants.
 *
 * All process I/O runs off the EDT (callers invoke [launch] on a pooled coroutine, never EDT);
 * engineering contract §1, §2. The [LaunchedServer.stop] callback is idempotent and is invoked
 * from the session teardown (risk-1.3 mitigation).
 */
class LuaRedisServerLauncher internal constructor(
    private val project: Project,
    private val seams: LaunchSeams,
) {

    /** Production constructor: uses the real tool resolver, PATH scanner, and [NetUtils] port allocation. */
    constructor(project: Project) : this(project, defaultSeams())

    /**
     * Launches a session-scoped local server for [provisioning] and returns a [LaunchedServer]
     * with the reachable host/port and an idempotent [LaunchedServer.stop] (design §3.9).
     *
     * Throws [ExecutionException] when the required binary or Docker executable cannot be located.
     * Must be called on a pooled coroutine — never the EDT (engineering contract §1).
     */
    suspend fun launch(provisioning: LuaRedisProvisioning): LaunchedServer = when (provisioning) {
        is LuaRedisProvisioning.LocalBinary -> launchBinary(provisioning)
        is LuaRedisProvisioning.Docker -> launchDocker(provisioning)
        is LuaRedisProvisioning.Remote ->
            throw IllegalArgumentException(
                "Remote provisioning does not start a local server; resolve host/port from the connection directly."
            )
    }

    private fun launchBinary(provisioning: LuaRedisProvisioning.LocalBinary): LaunchedServer {
        val binaryPath = seams.resolveToolPath(project, provisioning.toolKindId)
            ?: throw ExecutionException(
                "Redis/Valkey server binary not found — register it under " +
                    "Settings | Languages & Frameworks | Lua | Toolchain, or use Docker."
            )
        val freePort = seams.allocatePort()
        val commandLine = buildBinaryCommandLine(binaryPath, freePort)
        log.info("Launching Redis server: ${commandLine.commandLineString}")
        val handler = OSProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        handler.startNotify()
        return LaunchedServer(host = "127.0.0.1", port = freePort, stop = { stopHandler(handler) })
    }

    private fun launchDocker(provisioning: LuaRedisProvisioning.Docker): LaunchedServer {
        val dockerPath = seams.resolveDockerPath()
            ?: throw ExecutionException(
                "Docker is not available on PATH. Install Docker Desktop or a Docker CLI " +
                    "and ensure it is on the system PATH."
            )
        val freePort = seams.allocatePort()
        val commandLine = buildDockerCommandLine(dockerPath, provisioning.image, freePort)
        log.info("Launching Redis Docker container: ${commandLine.commandLineString}")
        val handler = OSProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        handler.startNotify()
        val containerId = readContainerId(handler)
        return LaunchedServer(
            host = "127.0.0.1",
            port = freePort,
            stop = { stopDockerContainer(dockerPath, containerId) },
        )
    }

    private fun readContainerId(handler: OSProcessHandler): String {
        handler.waitFor(5_000)
        return handler.process.inputStream.bufferedReader().readLine().orEmpty().trim()
    }

    private fun stopHandler(handler: OSProcessHandler) {
        try {
            if (!handler.isProcessTerminated) {
                handler.destroyProcess()
            }
        } catch (ex: Exception) {
            log.warn("Error stopping Redis server process", ex)
        }
    }

    private fun stopDockerContainer(dockerPath: String, containerId: String) {
        if (containerId.isBlank()) return
        try {
            Runtime.getRuntime().exec(arrayOf(dockerPath, "rm", "-f", containerId)).waitFor()
        } catch (ex: Exception) {
            log.warn("Error stopping Docker container $containerId", ex)
        }
    }
}

/**
 * Builds the [GeneralCommandLine] for a local Redis/Valkey server binary (design §3.9).
 *
 * Command: `<binary> --port <port> --save ""`
 *
 * Extracted as a package-internal function so [TestLuaRedisServerLauncher] can assert the
 * command-line shape (TC-LAUNCH-1) without spawning a real process.
 */
internal fun buildBinaryCommandLine(binaryPath: String, port: Int): GeneralCommandLine =
    GeneralCommandLine(binaryPath, "--port", port.toString(), "--save", "")

/**
 * Builds the [GeneralCommandLine] for a Docker-based Redis/Valkey container (design §3.9).
 *
 * Command: `<docker> run --rm -d -p <port>:6379 <image>`
 *
 * Extracted as a package-internal function so [TestLuaRedisServerLauncher] can assert the
 * command-line shape (TC-LAUNCH-2) without spawning a real container.
 */
internal fun buildDockerCommandLine(dockerPath: String, image: String, port: Int): GeneralCommandLine =
    GeneralCommandLine(dockerPath, "run", "--rm", "-d", "-p", "$port:6379", image)
