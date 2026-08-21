package net.internetisalie.lunar.redis.connection

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import net.internetisalie.lunar.toolchain.exec.LuaExecOutcome
import net.internetisalie.lunar.toolchain.exec.LuaExecResult

/**
 * TC-LAUNCH-1..3 (design §3.9, requirements AC-3): command-line assembly for binary and Docker
 * provisioning, and the "neither available" error path, verified without launching real processes.
 *
 * TC-LAUNCH-1: `LocalBinary` with a resolved path and a fixed port → correct binary command line.
 * TC-LAUNCH-2: `Docker` with docker on PATH and a fixed port → correct docker run command line.
 * TC-LAUNCH-3: `LocalBinary` with an unresolved binary AND no docker on PATH → [ExecutionException]
 *   whose message names both the missing binary (Settings path) and Docker as alternatives.
 *
 * The [LaunchSeams] injection prevents real process spawning; [buildBinaryCommandLine] and
 * [buildDockerCommandLine] are tested directly for TC-LAUNCH-1/2 to pin the exact argument list.
 * TC-LAUNCH-3 drives [LuaRedisServerLauncher.launch] end-to-end through seams that return null
 * for both resolution paths.
 */
class TestLuaRedisServerLauncher : BasePlatformTestCase() {
    /** TC-LAUNCH-1: binary command line is `redis-server --port 12345 --save ""` (design §3.9). */
    fun testBinaryCommandLineAssembly() {
        val commandLine = buildBinaryCommandLine("/usr/bin/redis-server", 12345)

        assertEquals("/usr/bin/redis-server", commandLine.exePath)
        assertEquals(
            listOf("--port", "12345", "--save", ""),
            commandLine.parametersList.list,
        )
    }

    /** TC-LAUNCH-2: docker command line is `docker run --rm -d -p 12345:6379 redis:8` (design §3.9). */
    fun testDockerCommandLineAssembly() {
        val commandLine = buildDockerCommandLine("/usr/bin/docker", "redis:8", 12345)

        assertEquals("/usr/bin/docker", commandLine.exePath)
        assertEquals(
            listOf("run", "--rm", "-d", "-p", "12345:6379", "redis:8"),
            commandLine.parametersList.list,
        )
    }

    /**
     * TC-LAUNCH-3: when the server binary cannot be resolved AND Docker is not on PATH, [launch]
     * throws [ExecutionException] whose message mentions both the Settings path and Docker (design §3.9).
     */
    fun testNeitherBinaryNorDockerThrowsExecutionException() {
        val neitherAvailableSeams =
            LaunchSeams(
                resolveToolPath = { _, _ -> null },
                resolveDockerPath = { null },
                allocatePort = { 12345 },
                execute = {
                    fail("resolution fails first — nothing should be executed")
                    error("unreachable")
                },
                awaitReady = { _, _ ->
                    fail("nothing was launched, so nothing should be awaited")
                    false
                },
            )
        val launcher = LuaRedisServerLauncher(myFixture.project, neitherAvailableSeams)
        val provisioning = LuaRedisProvisioning.LocalBinary("redis-server")

        try {
            runBlocking { launcher.launch(provisioning) }
            fail("Expected ExecutionException when binary is unresolved")
        } catch (ex: ExecutionException) {
            val message = ex.message.orEmpty()
            assertTrue(
                "Message should mention Settings/Toolchain path",
                message.contains("Settings") || message.contains("Toolchain"),
            )
            assertTrue(
                "Message should mention Docker as an alternative",
                message.contains("Docker"),
            )
        }
    }

    // ── BUG-446: process handling and readiness, the parts the seam did not previously reach ──────

    /**
     * The defect itself: the container id must come back from the captured output, and the `stop`
     * handed to the caller must remove **that** container.
     *
     * The original code read `handler.process.inputStream` after `startNotify()` had given that
     * stream to the platform's reader threads, so the id was empty or the read threw
     * `Stream closed`; a blank id then made `stopDockerContainer` return early, which is the leak.
     *
     * **Mutation proof** (bug-report §6): restore `readContainerId`'s direct stream read and this
     * test fails on the `docker rm -f` assertion, because the id it captures is blank. The test
     * cannot be run against the original source unchanged — widening [LaunchSeams] is what made this
     * path reachable at all — so the proof is a mutation rather than a red-then-green run, and
     * [LuaRedisServerLauncherDockerTest] covers the same ground against real Docker with no seams.
     */
    fun testDockerLaunchReadsTheContainerIdAndStopRemovesThatContainer() {
        val executed = mutableListOf<List<String>>()
        val launcher =
            LuaRedisServerLauncher(project, recordingSeams(executed, dockerRunOutput(CONTAINER_ID)))

        val server = runBlocking { launcher.launch(LuaRedisProvisioning.Docker("redis:8")) }
        server.stop()

        assertEquals("the launcher must report the port it allocated", PORT, server.port)
        assertEquals(
            "stop() must remove the container the launch started — a blank id silently skips cleanup",
            listOf(DOCKER, "rm", "-f", CONTAINER_ID),
            executed.last(),
        )
    }

    /** Readiness is gated on the port the launch actually allocated, not on some default. */
    fun testReadinessIsAwaitedOnTheAllocatedPort() {
        val probedPorts = mutableListOf<Int>()
        val seams =
            LaunchSeams(
                resolveToolPath = { _, _ -> null },
                resolveDockerPath = { DOCKER },
                allocatePort = { PORT },
                execute = { dockerRunOutput(CONTAINER_ID) },
                awaitReady = { _, port ->
                    probedPorts += port
                    true
                },
            )

        runBlocking { LuaRedisServerLauncher(project, seams).launch(LuaRedisProvisioning.Docker("redis:8")) }

        assertEquals("launch must not return before the server answers on its own port", listOf(PORT), probedPorts)
    }

    /**
     * A container that starts but never answers is **removed**, not abandoned.
     *
     * Failing the launch without cleaning up would reintroduce the leak by another route, so this
     * asserts the `docker rm -f` as well as the exception.
     */
    fun testAContainerThatNeverAnswersIsRemovedRatherThanLeaked() {
        val executed = mutableListOf<List<String>>()
        val seams =
            LaunchSeams(
                resolveToolPath = { _, _ -> null },
                resolveDockerPath = { DOCKER },
                allocatePort = { PORT },
                execute = { commandLine ->
                    executed += argumentsOf(commandLine)
                    dockerRunOutput(CONTAINER_ID)
                },
                awaitReady = { _, _ -> false },
            )

        try {
            runBlocking { LuaRedisServerLauncher(project, seams).launch(LuaRedisProvisioning.Docker("redis:8")) }
            fail("launch must fail when the server never accepts connections")
        } catch (expected: ExecutionException) {
            assertTrue(
                "the failure should name the port that stayed silent — got: ${expected.message}",
                expected.message.orEmpty().contains(PORT.toString()),
            )
        }

        assertEquals(
            "a launch that times out must still remove its container",
            listOf(DOCKER, "rm", "-f", CONTAINER_ID),
            executed.last(),
        )
    }

    /**
     * A failing `docker run` must surface docker's own reason.
     *
     * Previously the stderr was discarded and the blank id was returned as success, so the user got
     * a server pointing at a port where nothing would ever listen and no indication why.
     */
    fun testAFailedDockerRunThrowsCarryingDockerStderr() {
        val seams =
            LaunchSeams(
                resolveToolPath = { _, _ -> null },
                resolveDockerPath = { DOCKER },
                allocatePort = { PORT },
                execute = {
                    LuaExecResult(
                        ProcessOutput("", "docker: invalid reference format.", 125, false, false),
                        LuaExecOutcome.COMPLETED,
                    )
                },
                awaitReady = { _, _ ->
                    fail("a failed run must not reach the readiness gate")
                    false
                },
            )

        try {
            runBlocking { LuaRedisServerLauncher(project, seams).launch(LuaRedisProvisioning.Docker("INVALID")) }
            fail("launch must throw when docker run fails, rather than return a dead server")
        } catch (expected: ExecutionException) {
            val message = expected.message.orEmpty()
            assertTrue("the image should be named — got: $message", message.contains("INVALID"))
            assertTrue(
                "docker's own stderr should be carried — got: $message",
                message.contains("invalid reference format"),
            )
        }
    }

    /**
     * A container started, then **anything** thrown before `launch` returns, must still be removed.
     *
     * This is the cancellation case in disguise, and it is the one that matters in practice: the
     * readiness poll suspends in `delay`, so cancelling a run configuration resumes it with a
     * `CancellationException` while a container is already running and nothing yet holds a reference
     * to stop it. Found the empirical way — a `redis:8` container was left running on the builder by
     * a test whose failure landed in exactly that window.
     *
     * The throwable must also reach the caller unchanged; swallowing it here would turn a cancelled
     * run into a mysteriously succeeded one.
     */
    fun testAContainerIsRemovedWhenReadinessItselfFails() {
        val executed = mutableListOf<List<String>>()
        val seams =
            LaunchSeams(
                resolveToolPath = { _, _ -> null },
                resolveDockerPath = { DOCKER },
                allocatePort = { PORT },
                execute = { commandLine ->
                    executed += argumentsOf(commandLine)
                    dockerRunOutput(CONTAINER_ID)
                },
                awaitReady = { _, _ -> throw IllegalStateException("readiness interrupted") },
            )

        try {
            runBlocking { LuaRedisServerLauncher(project, seams).launch(LuaRedisProvisioning.Docker("redis:8")) }
            fail("the throwable from the readiness wait must reach the caller")
        } catch (expected: IllegalStateException) {
            assertEquals("readiness interrupted", expected.message)
        }

        assertEquals(
            "a container must not outlive a launch that failed while waiting for it",
            listOf(DOCKER, "rm", "-f", CONTAINER_ID),
            executed.last(),
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Seams for the Docker path that record every command line and answer readiness immediately.
     *
     * `resolveToolPath` returns null on purpose: nothing on the Docker path may consult it, and a
     * null makes that a failure rather than a silent fallback.
     */
    private fun recordingSeams(
        executed: MutableList<List<String>>,
        runResult: LuaExecResult,
    ): LaunchSeams =
        LaunchSeams(
            resolveToolPath = { _, _ -> null },
            resolveDockerPath = { DOCKER },
            allocatePort = { PORT },
            execute = { commandLine ->
                executed += argumentsOf(commandLine)
                if (executed.size == 1) runResult else dockerRunOutput("")
            },
            awaitReady = { _, _ -> true },
        )

    private fun argumentsOf(commandLine: GeneralCommandLine): List<String> =
        listOf(commandLine.exePath) + commandLine.parametersList.list

    /** `docker run -d` prints the container id and a trailing newline, which must be trimmed off. */
    private fun dockerRunOutput(containerId: String): LuaExecResult =
        LuaExecResult(ProcessOutput("$containerId\n", "", 0, false, false), LuaExecOutcome.COMPLETED)

    private companion object {
        const val DOCKER = "/usr/bin/docker"
        const val PORT = 12345
        const val CONTAINER_ID = "9f2c1ab77e04"
    }
}
