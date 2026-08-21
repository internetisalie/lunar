package net.internetisalie.lunar.redis.connection

import com.intellij.execution.ExecutionException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespEndpoint
import net.internetisalie.lunar.redis.resp.RespTimeouts
import net.internetisalie.lunar.redis.resp.RespValue

/**
 * BUG-446 — **the real Docker provisioning path, driven through [LuaRedisServerLauncher] itself.**
 *
 * The Docker suites in `src/redisIntegrationTest` start their containers with a raw `ProcessBuilder`,
 * under a KDoc describing the command as *"consistent with `LuaRedisServerLauncher`"*. They **mirror**
 * the launcher; not one of the three references it. That mirroring is why a defect on the first line
 * of the Docker happy path survived REDIS-01 through REDIS-06 while the docs recorded the path as
 * integration-tested (bug-report §4) — and the mirrors even worked *around* it, since each carries a
 * `waitForReady` poll of its own, so the readiness gap they were compensating for never showed up as
 * a failure. Production models that gate explicitly now, in [LuaRedisServerReadiness].
 *
 * So the point of this class is not that it uses Docker; it is that **every Docker call it makes goes
 * through production code**. The only raw `docker` invocations here are read-only assertions about
 * container existence, which is the one thing the launcher must not be trusted to report on.
 *
 * **Why it lives in `src/test` rather than the `redisIntegrationTest` source set.** It needs a real
 * [com.intellij.openapi.project.Project] to hand to the launcher, so it is a `BasePlatformTestCase`,
 * and only the IJPGP-configured `test` task supplies the platform JVM setup that requires —
 * `--add-opens`, `idea.home.path`, and the platform class loader, the last of which has to be set at
 * fork time. A hand-registered `Test` task supplies none of it, which is the same limitation
 * `build.gradle.kts` already records against the perf suites. It is kept out of the routine gate the
 * way the corpus sweeps are, by name: run it with `./gradlew test -PwithDocker`.
 *
 * Against the unfixed launcher both tests fail, for the two halves of the same defect:
 * `readContainerId` read `handler.process.inputStream` after `startNotify()` had handed it to the
 * platform's reader threads, so the id came back empty or the read threw `Stream closed`.
 */
class LuaRedisServerLauncherDockerTest : BasePlatformTestCase() {
    private val started = mutableListOf<LaunchedServer>()

    /**
     * Off the EDT, because [LuaRedisServerLauncher.launch]'s contract is that it never runs on it
     * (engineering contract §1) and the platform enforces that with a hard assertion.
     *
     * Leaving the default in place made this suite fail with *"Access from Event Dispatch Thread
     * (EDT) is not allowed"* — the test harness violating the very rule the code under test
     * documents, not a defect in the launcher.
     */
    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        val version = docker("version", "--format", "{{.Server.Version}}")
        assertTrue(
            "Docker environment check failed: the daemon is not reachable. This suite is opt-in " +
                "(`./gradlew test -PwithDocker`) and needs a running Docker daemon with access to " +
                "the 'redis:8' image.",
            version.exitCode == 0,
        )
    }

    /** Stop anything a failing assertion left behind, so one red test cannot leak into the next. */
    override fun tearDown() {
        try {
            started.forEach { server -> runCatching { server.stop() } }
            started.clear()
        } finally {
            super.tearDown()
        }
    }

    /**
     * The whole contract in one pass: the id is read, the server is **ready when `launch` returns**,
     * and `stop` actually removes the container.
     *
     * The readiness assertion is the deliberate part — it opens a client **once, with no retry
     * loop**, immediately after `launch` returns. A poll here would hide the very gap bug-report
     * §5b measured (3/3 runs connected-but-silent, ready only 31–70 ms later), because docker-proxy
     * binds the published port at container-create time and so a mere TCP connect succeeds while
     * nothing is listening inside the container.
     *
     * Container existence is asserted by port rather than by id, since [LaunchedServer] does not
     * expose the id — and should not have to for the leak to be observable.
     */
    fun testDockerLaunchIsReadyOnReturnAndStopRemovesTheContainer() {
        val server = launch(LuaRedisProvisioning.Docker(REDIS_IMAGE))

        assertEquals(
            "exactly one container must be publishing the launcher's allocated port",
            1,
            containersPublishing(server.port).size,
        )

        val endpoint = RespEndpoint(host = server.host, port = server.port)
        val reply =
            runBlocking {
                val client = RespClient.open(endpoint, IMPATIENT)
                try {
                    client.command("PING")
                } finally {
                    client.dispose()
                }
            }
        assertEquals(
            "the server must answer on the first attempt — launch() returned before it was listening",
            RespValue.Simple("PONG"),
            reply,
        )

        server.stop()
        started.remove(server)
        assertEquals(
            "stop() must remove the container — a blank container id makes stopDockerContainer " +
                "return early and the container outlives the IDE session",
            emptyList<String>(),
            containersPublishing(server.port),
        )
    }

    /**
     * A `docker run` that fails must surface its own diagnosis and leave nothing behind.
     *
     * `INVALID` is rejected for its reference format (an image name may not contain upper case), so
     * this fails locally and instantly — no registry round trip, and it works on a builder with no
     * egress. The unfixed launcher reports nothing at all here: stdout is empty, the id is blank,
     * and it hands back a [LaunchedServer] addressing a port where nothing will ever listen.
     */
    fun testAnUnusableImageFailsLoudlyAndLeavesNoContainer() {
        val before = allContainerIds()

        val failure =
            try {
                launch(LuaRedisProvisioning.Docker("INVALID"))
                fail("launch must throw when docker run fails, rather than return a dead server")
                return
            } catch (expected: ExecutionException) {
                expected
            }

        assertTrue(
            "the failure must carry docker's own reason, not just a generic message — got: ${failure.message}",
            failure.message.orEmpty().contains("INVALID") || failure.message.orEmpty().contains("reference format"),
        )
        assertEquals("a failed launch must not leave a container behind", before, allContainerIds())
    }

    private fun launch(provisioning: LuaRedisProvisioning): LaunchedServer {
        val server = runBlocking { LuaRedisServerLauncher(project).launch(provisioning) }
        started.add(server)
        return server
    }

    /** Container ids publishing [port] — a read-only check the launcher has no part in. */
    private fun containersPublishing(port: Int): List<String> =
        docker("ps", "-a", "--filter", "publish=$port", "--format", "{{.ID}}")
            .stdout
            .lines()
            .filter { it.isNotBlank() }

    private fun allContainerIds(): Set<String> =
        docker("ps", "-aq")
            .stdout
            .lines()
            .filter { it.isNotBlank() }
            .toSet()

    private fun docker(vararg args: String): DockerResult {
        val process = ProcessBuilder(listOf("docker") + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return DockerResult(process.exitValue(), output)
    }

    private data class DockerResult(
        val exitCode: Int,
        val stdout: String,
    )

    private companion object {
        const val REDIS_IMAGE = "redis:8"

        /**
         * Short enough that a server which is not yet listening fails the assertion rather than
         * quietly waiting for the default 5 s connect / 30 s read to cover the gap up.
         */
        val IMPATIENT = RespTimeouts(connectMs = 1_000, readMs = 2_000)
    }
}
