package net.internetisalie.lunar.redis

import kotlinx.coroutines.runBlocking
import net.internetisalie.lunar.redis.functions.LuaRedisFunctionListParser
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespEndpoint
import net.internetisalie.lunar.redis.resp.RespTimeouts
import net.internetisalie.lunar.redis.resp.RespValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

/**
 * Dockerized integration test: Redis Functions workflow (FUNCTION LOAD / LIST / FCALL / REPLACE /
 * DELETE) against real `redis:8` and `valkey/valkey:8` containers (REDIS-05 design §6, TC-INT-1,
 * AC-10).
 *
 * Covers the REDIS-05 epic's dual-flavor compatibility contract. Requires Docker on PATH; fails
 * loudly with a clear environment message when Docker is absent (RISK-R10 / design §7 "fail
 * loudly"). Run via the `redisIntegrationTest` Gradle task.
 *
 * Container lifecycle: one container per [Test] method, started in the test, stopped in [After].
 * Uses raw `docker run --rm -d -p <port>:6379 <image>` (consistent with [RedisIntegrationTest]).
 */
class RedisFunctionsIntegrationTest {

    private val containers = mutableListOf<RunningContainer>()

    @Before
    fun assertDockerAvailable() {
        val process = runCatching {
            ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                .redirectErrorStream(true)
                .start()
                .also { it.waitFor() }
        }.getOrNull()
        if (process?.exitValue() != 0) {
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

    /** TC-INT-1 (redis:8): load, list, call, replace, delete each succeed. */
    @Test
    fun testRedisFunctionsWorkflow() {
        val container = startContainer("redis:8")
        runFunctionsSuite(container, "redis:8")
    }

    /** TC-INT-1 (valkey/valkey:8): load, list, call, replace, delete each succeed. */
    @Test
    fun testValkeyFunctionsWorkflow() {
        val container = startContainer("valkey/valkey:8")
        runFunctionsSuite(container, "valkey/valkey:8")
    }

    // ── per-flavor suite ──────────────────────────────────────────────────────────────────────────

    private fun runFunctionsSuite(container: RunningContainer, flavorLabel: String) {
        withClient(container.endpoint()) { client ->
            deployLibrary(client, flavorLabel, LIB_BODY_V1)
            verifyList(client, flavorLabel)
            verifyFcall(client, flavorLabel, LIB_BODY_V1)
            verifyReplace(client, flavorLabel)
            verifyDelete(client, flavorLabel)
        }
    }

    // ── step helpers ──────────────────────────────────────────────────────────────────────────────

    private fun deployLibrary(client: RespClient, flavorLabel: String, body: String) {
        val reply = runBlocking { client.command(buildLoadArgs(body, replace = true)) }
        val libName = when (reply) {
            is RespValue.Bulk -> reply.asString()
            is RespValue.Simple -> reply.text
            else -> null
        }
        assertEquals(
            "$flavorLabel FUNCTION LOAD: expected library name '$LIB_NAME' in reply",
            LIB_NAME,
            libName,
        )
    }

    private fun verifyList(client: RespClient, flavorLabel: String) {
        val reply = runBlocking { client.command("FUNCTION", "LIST") }
        val entries = LuaRedisFunctionListParser.parse(reply)
        val lib = entries.find { it.name == LIB_NAME }
        assertTrue(
            "$flavorLabel FUNCTION LIST: expected library '$LIB_NAME'; got ${entries.map { it.name }}",
            lib != null,
        )
        val fn = lib?.functions?.find { it.name == FN_NAME }
        assertTrue(
            "$flavorLabel FUNCTION LIST: expected function '$FN_NAME' in '$LIB_NAME'; " +
                "got ${lib?.functions?.map { it.name }}",
            fn != null,
        )
    }

    private fun verifyFcall(client: RespClient, flavorLabel: String, body: String) {
        val reply = runBlocking { client.command("FCALL", FN_NAME, "0") }
        val expected = expectedReplyFor(body)
        assertEquals("$flavorLabel FCALL $FN_NAME: expected $expected", expected, reply)
    }

    private fun verifyReplace(client: RespClient, flavorLabel: String) {
        deployLibrary(client, flavorLabel, LIB_BODY_V2)
        verifyFcall(client, flavorLabel, LIB_BODY_V2)
    }

    private fun verifyDelete(client: RespClient, flavorLabel: String) {
        val delReply = runBlocking { client.command("FUNCTION", "DELETE", LIB_NAME) }
        val okText = (delReply as? RespValue.Simple)?.text
        assertEquals("$flavorLabel FUNCTION DELETE: expected +OK", "OK", okText)

        val listReply = runBlocking { client.command("FUNCTION", "LIST") }
        val entries = LuaRedisFunctionListParser.parse(listReply)
        assertFalse(
            "$flavorLabel after DELETE: '$LIB_NAME' must be absent; got ${entries.map { it.name }}",
            entries.any { it.name == LIB_NAME },
        )
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
        fail(
            "Container for image '$image' did not become ready within ${READY_TIMEOUT_MS}ms. " +
                "Last error: $lastError",
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────────

    private fun withClient(endpoint: RespEndpoint, block: (RespClient) -> Unit) {
        val client = runBlocking { RespClient.open(endpoint) }
        try {
            block(client)
        } finally {
            client.dispose()
        }
    }

    private fun buildLoadArgs(body: String, replace: Boolean): List<ByteArray> {
        val parts = mutableListOf("FUNCTION", "LOAD")
        if (replace) parts.add("REPLACE")
        parts.add(body)
        return parts.map { it.toByteArray(Charsets.UTF_8) }
    }

    private fun expectedReplyFor(body: String): RespValue =
        if (body == LIB_BODY_V1) RespValue.Integer(42) else RespValue.Integer(99)

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
        private const val LIB_NAME: String = "mylib"
        private const val FN_NAME: String = "myfunc"

        /** V1 library body: [FN_NAME] returns integer 42 (deterministic, flavor-neutral). */
        private const val LIB_BODY_V1: String =
            "#!lua name=mylib\nredis.register_function('myfunc', function(keys, args) return 42 end)"

        /** V2 library body: [FN_NAME] returns integer 99 (proves FUNCTION LOAD REPLACE took effect). */
        private const val LIB_BODY_V2: String =
            "#!lua name=mylib\nredis.register_function('myfunc', function(keys, args) return 99 end)"

        private const val READY_TIMEOUT_MS: Long = 30_000L
        private const val READY_POLL_MS: Long = 500L
    }
}
