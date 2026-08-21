package net.internetisalie.lunar.redis.connection

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.internetisalie.lunar.toolchain.exec.LuaExecResult
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver

private val log = logger<LuaRedisServerLauncher>()

/** Every launched server is bound to the loopback interface — see [buildDockerCommandLine]. */
private const val LOCALHOST = "127.0.0.1"

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
 *
 * BUG-446 added [execute] and [awaitReady]. The seam previously stopped at *resolution*, so process
 * execution and readiness — the two things the launcher actually got wrong — were unreachable from a
 * test, and the suite could only assert command-line shapes that were already correct. Five seams is
 * past the engineering contract's 3-argument cap, which the contract itself answers: surplus
 * parameter state belongs in "a dedicated configuration or execution context class", and this is
 * that class.
 */
internal class LaunchSeams(
    val resolveToolPath: (project: Project, toolKindId: String) -> String?,
    val resolveDockerPath: () -> String?,
    val allocatePort: () -> Int,
    val execute: (GeneralCommandLine) -> LuaExecResult,
    val awaitReady: suspend (host: String, port: Int) -> Boolean,
)

private fun defaultSeams(): LaunchSeams =
    LaunchSeams(
        resolveToolPath = { project, kindId ->
            LuaToolResolver.getInstance().resolve(project, kindId)?.path
        },
        resolveDockerPath = {
            PathEnvironmentVariableUtil.findInPath("docker")?.absolutePath
        },
        allocatePort = { NetUtils.findAvailableSocketPort() },
        execute = { commandLine ->
            // NETWORK, not COMMAND: `docker run` pulls the image when it is not cached locally.
            LuaToolExecutionService.getInstance().capture(commandLine, LuaExecTimeout.NETWORK)
        },
        awaitReady = { host, port -> LuaRedisServerReadiness().awaitReply(host, port) },
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
    suspend fun launch(provisioning: LuaRedisProvisioning): LaunchedServer =
        when (provisioning) {
            is LuaRedisProvisioning.LocalBinary -> launchBinary(provisioning)
            is LuaRedisProvisioning.Docker -> launchDocker(provisioning)
            is LuaRedisProvisioning.Remote ->
                throw IllegalArgumentException(
                    "Remote provisioning does not start a local server; resolve host/port from the connection directly.",
                )
        }

    private suspend fun launchBinary(provisioning: LuaRedisProvisioning.LocalBinary): LaunchedServer {
        val binaryPath =
            seams.resolveToolPath(project, provisioning.toolKindId)
                ?: throw ExecutionException(
                    "Redis/Valkey server binary not found — register it under " +
                        "Settings | Languages & Frameworks | Lua | Toolchain, or use Docker.",
                )
        val freePort = seams.allocatePort()
        val commandLine = buildBinaryCommandLine(binaryPath, freePort)
        log.info("Launching Redis server: ${commandLine.commandLineString}")
        val handler = OSProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        handler.startNotify()
        return readyServer(freePort) { stopHandler(handler) }
    }

    private suspend fun launchDocker(provisioning: LuaRedisProvisioning.Docker): LaunchedServer {
        val dockerPath =
            seams.resolveDockerPath()
                ?: throw ExecutionException(
                    "Docker is not available on PATH. Install Docker Desktop or a Docker CLI " +
                        "and ensure it is on the system PATH.",
                )
        val freePort = seams.allocatePort()
        val commandLine = buildDockerCommandLine(dockerPath, provisioning.image, freePort)
        log.info("Launching Redis Docker container: ${commandLine.commandLineString}")
        val containerId = startContainer(commandLine, provisioning.image)
        return readyServer(freePort) { stopDockerContainer(dockerPath, containerId) }
    }

    /**
     * BUG-446 — **no launch path may hand back a server that is not answering yet.**
     *
     * Measured on the builder, neither path ever was at the moment its process had been spawned
     * (bug-report §5b); see [LuaRedisServerReadiness] for why the check has to be protocol-level
     * rather than a TCP connect. Shared by both paths deliberately: the gap was measured on both,
     * and shipping the gate on only one is the omission that produced this bug in the first place.
     *
     * A server that never answers is **stopped** before the failure is raised. Leaving it running is
     * how the original defect leaked a container per attempt, and a timeout must not reintroduce it.
     *
     * The wait is stopped on **any** throwable, not just on a false answer, and the reason is
     * cancellation: the readiness poll suspends in `delay`, so cancelling the run configuration
     * resumes it with a `CancellationException` — and a container started moments earlier would
     * outlive the session with nothing holding a reference to stop it. Found by noticing a leaked
     * `redis:8` on the builder after a run whose failure arrived between the container starting and
     * `launch` returning; the exception is rethrown untouched once the cleanup has run.
     */
    private suspend fun readyServer(
        port: Int,
        stop: () -> Unit,
    ): LaunchedServer {
        val ready =
            try {
                seams.awaitReady(LOCALHOST, port)
            } catch (failure: Throwable) {
                stop()
                throw failure
            }
        if (!ready) {
            stop()
            throw ExecutionException(
                "The Redis server started but never accepted connections on port $port. " +
                    "Check that the image or binary runs a Redis-compatible server.",
            )
        }
        return LaunchedServer(host = LOCALHOST, port = port, stop = stop)
    }

    /**
     * Runs `docker run -d` and returns the container id it prints.
     *
     * BUG-446 — the id is captured **through** the process handler rather than alongside it.
     * [LuaToolExecutionService.capture] wraps `CapturingProcessHandler`, so the platform remains the
     * single reader of the process's stdout. The original code called `startNotify()` — which hands
     * that stream to the platform's reader threads — and then read `process.inputStream` itself; the
     * loser of that race got nothing or an `IOException: Stream closed`.
     *
     * A failed `docker run` **throws, carrying docker's own stderr**. Returning a blank id was the
     * other half of the defect: `stopDockerContainer` returns early on a blank id, so a failure to
     * start also disabled the cleanup for anything that did start.
     *
     * On [Dispatchers.IO] because the capture blocks and its timeout is `NETWORK` — a first run
     * has to pull the image, which the old 5-second `waitFor` could never have waited out anyway.
     */
    private suspend fun startContainer(
        commandLine: GeneralCommandLine,
        image: String,
    ): String {
        val result = withContext(Dispatchers.IO) { seams.execute(commandLine) }
        val containerId =
            result.stdout
                .lines()
                .firstOrNull()
                ?.trim()
                .orEmpty()
        if (!result.isSuccess || containerId.isEmpty()) {
            throw ExecutionException("Failed to start a Redis container from image '$image': ${reasonFor(result)}")
        }
        return containerId
    }

    private fun reasonFor(result: LuaExecResult): String =
        result.stderr.trim().ifEmpty {
            "docker exited with code ${result.exitCode} and printed no container id"
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

    /**
     * Removes the container, through the same seam the launch used so a test can observe it
     * (bug-report §6) — the leak was invisible precisely because this call bypassed every seam.
     */
    private fun stopDockerContainer(
        dockerPath: String,
        containerId: String,
    ) {
        if (containerId.isBlank()) return
        try {
            seams.execute(buildDockerRemoveCommandLine(dockerPath, containerId))
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
internal fun buildBinaryCommandLine(
    binaryPath: String,
    port: Int,
): GeneralCommandLine = GeneralCommandLine(binaryPath, "--port", port.toString(), "--save", "")

/**
 * Builds the [GeneralCommandLine] for a Docker-based Redis/Valkey container (design §3.9).
 *
 * Command: `<docker> run --rm -d -p <port>:6379 <image>`
 *
 * Extracted as a package-internal function so [TestLuaRedisServerLauncher] can assert the
 * command-line shape (TC-LAUNCH-2) without spawning a real container.
 */
internal fun buildDockerCommandLine(
    dockerPath: String,
    image: String,
    port: Int,
): GeneralCommandLine = GeneralCommandLine(dockerPath, "run", "--rm", "-d", "-p", "$port:6379", image)

/**
 * Builds the [GeneralCommandLine] that removes a launched container (design §3.9, BUG-446).
 *
 * Command: `<docker> rm -f <containerId>`
 *
 * Extracted like its siblings so the stop path is assertable without a real container — the leak in
 * BUG-446 was only ever observable at this seam.
 */
internal fun buildDockerRemoveCommandLine(
    dockerPath: String,
    containerId: String,
): GeneralCommandLine = GeneralCommandLine(dockerPath, "rm", "-f", containerId)
