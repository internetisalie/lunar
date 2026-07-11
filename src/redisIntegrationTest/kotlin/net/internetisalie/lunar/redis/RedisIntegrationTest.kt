package net.internetisalie.lunar.redis

import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespEndpoint
import net.internetisalie.lunar.redis.resp.RespTimeouts
import net.internetisalie.lunar.redis.resp.RespValue
import net.internetisalie.lunar.redis.run.LuaRedisExecContext
import net.internetisalie.lunar.redis.run.LuaRedisExecMode
import net.internetisalie.lunar.redis.run.LuaRedisScriptExecutor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Dockerized integration test: EVAL + EVALSHA + `_RO` against real `redis:8` and `valkey/valkey:8`
 * containers (design §7, impl-plan Phase 6, TC-INT-1, AC-10).
 *
 * Covers the epic's dual-flavor compatibility contract. Requires Docker on PATH; fails loudly with a
 * clear environment message when Docker is absent (RISK-R10 / design §7 "fail loudly"). Run via the
 * `redisIntegrationTest` Gradle task, which is explicitly excluded from `build` / `test` (RISK-R10).
 *
 * Container lifecycle: one container per flavor, started per test, stopped in [@After]. Uses raw
 * `docker run --rm -d -p <port>:6379 <image>` (consistent with [LuaRedisServerLauncher]).
 */
class RedisIntegrationTest {

    private val containers = mutableListOf<RunningContainer>()

    @Before
    fun assertDockerAvailable() {
        val result = runCatching {
            ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                .redirectErrorStream(true)
                .start()
                .also { it.waitFor() }
        }
        val process = result.getOrNull()
        val exitOk = process?.exitValue() == 0
        if (!exitOk) {
            fail(
                "Docker environment check failed: Docker is not available on PATH or the Docker daemon " +
                    "is not running. The redisIntegrationTest task requires a running Docker daemon with " +
                    "access to 'redis:8' and 'valkey/valkey:8' images. " +
                    "Install Docker and ensure the daemon is started before running this task."
            )
        }
    }

    @After
    fun stopContainers() {
        containers.forEach { it.stop() }
        containers.clear()
    }

    /** TC-INT-1 (redis:8): EVAL, EVALSHA, EVAL_RO each return the expected reply. */
    @Test
    fun testRedisEvalAndEvalShaAndReadOnly() {
        val container = startContainer("redis:8")
        runFlavourSuite(container, flavorLabel = "redis:8")
    }

    /** TC-INT-1 (valkey/valkey:8): EVAL, EVALSHA, EVAL_RO each return the expected reply. */
    @Test
    fun testValkeyEvalAndEvalShaAndReadOnly() {
        val container = startContainer("valkey/valkey:8")
        runFlavourSuite(container, flavorLabel = "valkey/valkey:8")
    }

    // ── per-flavor suite ──────────────────────────────────────────────────────────────────────────

    private fun runFlavourSuite(container: RunningContainer, flavorLabel: String) {
        withClient(container.endpoint()) { client ->
            verifyEval(client, flavorLabel)
            verifyEvalSha(client, flavorLabel)
            verifyEvalReadOnly(client, flavorLabel)
        }
    }

    private fun verifyEval(client: RespClient, flavorLabel: String) {
        val context = execContext(LuaRedisExecMode.EVAL, readOnly = false)
        val reply = runBlocking { LuaRedisScriptExecutor().execute(client, context, RETURN_42_SCRIPT) }
        assertEquals("$flavorLabel EVAL: expected integer 42", RespValue.Integer(42), reply)
    }

    private fun verifyEvalSha(client: RespClient, flavorLabel: String) {
        val context = execContext(LuaRedisExecMode.EVALSHA, readOnly = false)
        val reply = runBlocking { LuaRedisScriptExecutor().execute(client, context, RETURN_42_SCRIPT) }
        assertEquals("$flavorLabel EVALSHA: expected integer 42", RespValue.Integer(42), reply)
    }

    private fun verifyEvalReadOnly(client: RespClient, flavorLabel: String) {
        val context = execContext(LuaRedisExecMode.EVAL, readOnly = true)
        val reply = runBlocking { LuaRedisScriptExecutor().execute(client, context, RETURN_42_SCRIPT) }
        assertEquals("$flavorLabel EVAL_RO: expected integer 42", RespValue.Integer(42), reply)
    }

    // ── Docker helpers ────────────────────────────────────────────────────────────────────────────

    private fun startContainer(image: String): RunningContainer {
        val port = ServerSocket(0).use { it.localPort }
        val process = ProcessBuilder(
            "docker", "run", "--rm", "-d", "-p", "$port:6379", image,
        ).redirectErrorStream(true).start()
        process.waitFor()
        val containerId = process.inputStream.bufferedReader().readLine().orEmpty().trim()
        if (containerId.isBlank()) {
            fail("Failed to start Docker container for image '$image': docker run produced no container id")
        }
        val container = RunningContainer(containerId, port)
        containers.add(container)
        waitForReady(container.endpoint(), image)
        return container
    }

    private fun waitForReady(endpoint: RespEndpoint, image: String) {
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        var lastError: Throwable? = null
        val shortTimeouts = RespTimeouts(connectMs = 500, readMs = 1_000)
        while (System.currentTimeMillis() < deadline) {
            val pingResult = runCatching {
                runBlocking {
                    val client = RespClient.open(endpoint, shortTimeouts)
                    try {
                        client.command("PING")
                    } finally {
                        client.dispose()
                    }
                }
            }
            val reply = pingResult.getOrNull()
            if (reply is RespValue.Simple && reply.text == "PONG") return
            lastError = pingResult.exceptionOrNull()
            Thread.sleep(READY_POLL_MS)
        }
        fail("Container for image '$image' did not become ready within ${READY_TIMEOUT_MS}ms. Last error: $lastError")
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────────

    private fun execContext(mode: LuaRedisExecMode, readOnly: Boolean): LuaRedisExecContext =
        LuaRedisExecContext(
            connectionId = UUID.randomUUID().toString(),
            execMode = mode,
            readOnly = readOnly,
            keys = emptyList(),
            argv = emptyList(),
        )

    private fun withClient(endpoint: RespEndpoint, block: (RespClient) -> Unit) {
        val client = runBlocking { RespClient.open(endpoint) }
        try {
            block(client)
        } finally {
            client.dispose()
        }
    }

    // ── inner types ───────────────────────────────────────────────────────────────────────────────

    private inner class RunningContainer(private val containerId: String, val port: Int) {

        fun endpoint(): RespEndpoint = RespEndpoint(host = "127.0.0.1", port = port)

        fun stop() {
            if (containerId.isBlank()) return
            runCatching {
                ProcessBuilder("docker", "rm", "-f", containerId)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
            }
        }
    }

    companion object {
        private const val RETURN_42_SCRIPT: String = "return 42"
        private const val READY_TIMEOUT_MS: Long = 30_000L
        private const val READY_POLL_MS: Long = 500L
    }
}
