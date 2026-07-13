package net.internetisalie.lunar.redis

import kotlinx.coroutines.runBlocking
import net.internetisalie.lunar.redis.debug.EndReason
import net.internetisalie.lunar.redis.debug.LdbCommand
import net.internetisalie.lunar.redis.debug.LdbEvent
import net.internetisalie.lunar.redis.debug.LdbPrintParser
import net.internetisalie.lunar.redis.debug.LdbReplyParser
import net.internetisalie.lunar.redis.debug.LuaLdbTransport
import net.internetisalie.lunar.redis.debug.LuaRedisDebugMode
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespEndpoint
import net.internetisalie.lunar.redis.resp.RespTimeouts
import net.internetisalie.lunar.redis.resp.RespValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

/**
 * Dual-flavor LDB debug-adapter integration test against real `redis:8` and `valkey/valkey:8`
 * (design §3.1/§3.3/§3.4, requirements TC-INT-1/2/3, AC-10). The compatibility contract for the
 * whole LDB stack (epic RISK-R01/R10): the Phase-1 [LdbReplyParser]/[LdbPrintParser] and the
 * [LuaLdbTransport] over the REDIS-01 [RespClient] are driven against the live server and asserted
 * to parse its real framing.
 *
 * Container lifecycle mirrors [RedisIntegrationTest]: `docker run --rm -d -p <port>:6379` per test,
 * torn down in [@After]. Fails loudly (not skips) when Docker is unavailable (RISK-R10). Wired under
 * the `redisIntegrationTest` Gradle task (REDIS-01 Phase 6 / DR-04), excluded from `build`/`test`.
 *
 * Live-framing note (Phase 5 / DR-06, folded into design §3.3): both servers signal session end with
 * a `["<endsession>"]` block (never the design's assumed `"* Lua debugging session ended"`); the real
 * `EVAL` result / abort error arrives as a separate trailing block drained via [RespClient.readReply];
 * `redis <cmd>` replies are rendered as `<redis> …` / `<reply> …` status lines. [LdbReplyParser] was
 * hardened for the `<endsession>` sentinel as part of this phase.
 */
class RedisDebugIntegrationTest {

    private val containers = mutableListOf<RunningContainer>()

    @Before
    fun assertDockerAvailable() {
        val result = runCatching {
            ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                .redirectErrorStream(true)
                .start()
                .also { it.waitFor() }
        }
        val exitOk = result.getOrNull()?.exitValue() == 0
        if (!exitOk) {
            fail(
                "Docker environment check failed: Docker is not available on PATH or the Docker daemon " +
                    "is not running. The redisIntegrationTest task requires a running Docker daemon with " +
                    "access to 'redis:8' and 'valkey/valkey:8' images. " +
                    "Install Docker and ensure the daemon is started before running this task.",
            )
        }
    }

    @After
    fun stopContainers() {
        containers.forEach { it.stop() }
        containers.clear()
    }

    /** TC-INT-1 (redis:8): break → step → print locals → continue over a real EVAL script. */
    @Test
    fun testRedisBreakStepPrintContinue() {
        withTransport("redis:8") { transport -> verifyBreakStepPrintContinue(transport, "redis:8") }
    }

    /** TC-INT-1 (valkey/valkey:8): break → step → print locals → continue over a real EVAL script. */
    @Test
    fun testValkeyBreakStepPrintContinue() {
        withTransport("valkey/valkey:8") { transport -> verifyBreakStepPrintContinue(transport, "valkey/valkey:8") }
    }

    /** TC-INT-2 (redis:8): a mid-pause `redis` command returns a real reply. */
    @Test
    fun testRedisMidPauseRedisCommand() {
        withTransport("redis:8") { transport -> verifyMidPauseRedisCommand(transport, "redis:8") }
    }

    /** TC-INT-2 (valkey/valkey:8): a mid-pause `redis` command returns a real reply. */
    @Test
    fun testValkeyMidPauseRedisCommand() {
        withTransport("valkey/valkey:8") { transport -> verifyMidPauseRedisCommand(transport, "valkey/valkey:8") }
    }

    /** TC-INT-3 (redis:8): forked `abort` rolls a script write back; session-end is detected. */
    @Test
    fun testRedisForkedAbortRollback() {
        val container = startContainer("redis:8")
        verifyForkedAbortRollback(container, "redis:8")
    }

    /** TC-INT-3 (valkey/valkey:8): forked `abort` rolls a script write back; session-end is detected. */
    @Test
    fun testValkeyForkedAbortRollback() {
        val container = startContainer("valkey/valkey:8")
        verifyForkedAbortRollback(container, "valkey/valkey:8")
    }

    // ── TC-INT-1 ────────────────────────────────────────────────────────────────────────────────────

    private fun verifyBreakStepPrintContinue(transport: LuaLdbTransport, flavor: String) = runBlocking {
        enterDebug(transport, flavor)
        val enterReply = transport.eval(FIVE_LINE_SCRIPT, keys = emptyList(), argv = emptyList())
        val enterStop = LdbReplyParser.parse(enterReply)
        assertTrue("$flavor: EVAL should enter at a stop, was $enterStop", enterStop is LdbEvent.Stop)

        val breakEvent = LdbReplyParser.parse(transport.send(LdbCommand.Break(BREAKPOINT_LINE)))
        assertTrue("$flavor: break 3 should be an Ack/status, was $breakEvent", breakEvent is LdbEvent.Ack)

        val stopAtBreak = LdbReplyParser.parse(transport.send(LdbCommand.Continue))
        assertStop(stopAtBreak, BREAKPOINT_LINE, "$flavor: continue should stop at breakpoint line 3")

        val localsReply = transport.send(LdbCommand.Print(varName = null))
        val locals = LdbPrintParser.parseLocals(localsReply).associate { it.name to it.value }
        assertTrue("$flavor: print should expose local x, saw ${locals.keys}", locals.containsKey("x"))
        assertTrue("$flavor: print should expose local y, saw ${locals.keys}", locals.containsKey("y"))

        val stepEvent = LdbReplyParser.parse(transport.send(LdbCommand.Step))
        assertTrue("$flavor: step should produce a stop, was $stepEvent", stepEvent is LdbEvent.Stop)
        val steppedLine = (stepEvent as LdbEvent.Stop).serverLine
        assertTrue("$flavor: step should advance past line 3, was $steppedLine", steppedLine > BREAKPOINT_LINE)

        val endEvent = LdbReplyParser.parse(transport.send(LdbCommand.Continue))
        assertEquals("$flavor: continue-to-completion should end the session", ended(), endEvent)
        val finalResult = transport.readReply()
        assertEquals("$flavor: the EVAL result should be integer 3 (x+y)", RespValue.Integer(3), finalResult)
    }

    // ── TC-INT-2 ────────────────────────────────────────────────────────────────────────────────────

    private fun verifyMidPauseRedisCommand(transport: LuaLdbTransport, flavor: String) = runBlocking {
        enterDebug(transport, flavor)
        val enterStop = LdbReplyParser.parse(transport.eval(FIVE_LINE_SCRIPT, emptyList(), emptyList()))
        assertTrue("$flavor: EVAL should enter at a stop", enterStop is LdbEvent.Stop)

        val setReply = transport.send(LdbCommand.RedisCmd(listOf("SET", REDIS_TAB_KEY, "1")))
        assertRedisReplyMentions(setReply, "SET", flavor)

        val getReply = transport.send(LdbCommand.RedisCmd(listOf("GET", REDIS_TAB_KEY)))
        assertRedisReplyMentions(getReply, "1", flavor)

        transport.send(LdbCommand.Abort)
    }

    // ── TC-INT-3 ────────────────────────────────────────────────────────────────────────────────────

    private fun verifyForkedAbortRollback(container: RunningContainer, flavor: String) {
        withTransport(container) { transport -> abortForkedSession(transport, flavor) }
        val afterAbort = readKeyOnFreshClient(container, ROLLBACK_KEY)
        assertNull("$flavor: a forked-session write must be rolled back after abort", afterAbort)
    }

    private fun abortForkedSession(transport: LuaLdbTransport, flavor: String) = runBlocking {
        enterDebug(transport, flavor)
        val enterStop = LdbReplyParser.parse(transport.eval(WRITE_SCRIPT, emptyList(), emptyList()))
        assertTrue("$flavor: forked EVAL should enter at a stop", enterStop is LdbEvent.Stop)

        val abortEvent = LdbReplyParser.parse(transport.send(LdbCommand.Abort))
        assertTrue("$flavor: abort should end the session, was $abortEvent", abortEvent is LdbEvent.SessionEnded)
    }

    // ── LDB helpers ─────────────────────────────────────────────────────────────────────────────────

    private suspend fun enterDebug(transport: LuaLdbTransport, flavor: String) {
        val ok = transport.enterDebug(LuaRedisDebugMode.FORKED)
        assertEquals("$flavor: SCRIPT DEBUG YES should return +OK", RespValue.Simple("OK"), ok)
    }

    private fun ended(): LdbEvent = LdbEvent.SessionEnded(EndReason.ENDED)

    private fun assertStop(event: LdbEvent, expectedLine: Int, message: String) {
        assertTrue("$message — expected a Stop, was $event", event is LdbEvent.Stop)
        assertEquals(message, expectedLine, (event as LdbEvent.Stop).serverLine)
    }

    private fun assertRedisReplyMentions(reply: RespValue, needle: String, flavor: String) {
        val text = statusText(reply)
        assertTrue("$flavor: mid-pause redis reply '$text' should mention '$needle'", text.contains(needle))
    }

    private fun statusText(reply: RespValue): String = when (reply) {
        is RespValue.Array -> reply.items.orEmpty().joinToString(" | ") { statusText(it) }
        is RespValue.Simple -> reply.text
        is RespValue.Bulk -> reply.asString().orEmpty()
        is RespValue.Error -> "${reply.klass} ${reply.message}"
        else -> reply.toString()
    }

    private fun readKeyOnFreshClient(container: RunningContainer, key: String): String? =
        runBlocking {
            val client = RespClient.open(container.endpoint())
            try {
                when (val reply = client.command("GET", key)) {
                    is RespValue.Bulk -> reply.asString()
                    is RespValue.Simple -> reply.text
                    else -> null
                }
            } finally {
                client.dispose()
            }
        }

    // ── transport / container plumbing ───────────────────────────────────────────────────────────────

    private fun withTransport(image: String, block: (LuaLdbTransport) -> Unit) {
        val container = startContainer(image)
        withTransport(container, block)
    }

    private fun withTransport(container: RunningContainer, block: (LuaLdbTransport) -> Unit) {
        val client = runBlocking { RespClient.open(container.endpoint()) }
        val transport = LuaLdbTransport(client)
        try {
            block(transport)
        } finally {
            transport.dispose()
        }
    }

    private fun startContainer(image: String): RunningContainer {
        val port = ServerSocket(0).use { it.localPort }
        val process = ProcessBuilder("docker", "run", "--rm", "-d", "-p", "$port:6379", image)
            .redirectErrorStream(true)
            .start()
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
            if ((pingResult.getOrNull() as? RespValue.Simple)?.text == "PONG") return
            lastError = pingResult.exceptionOrNull()
            Thread.sleep(READY_POLL_MS)
        }
        fail("Container for image '$image' did not become ready within ${READY_TIMEOUT_MS}ms. Last error: $lastError")
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

    private companion object {
        const val BREAKPOINT_LINE = 3
        const val REDIS_TAB_KEY = "ldb_tab_key"
        const val ROLLBACK_KEY = "ldb_rollback_key"
        const val READY_TIMEOUT_MS = 30_000L
        const val READY_POLL_MS = 500L

        val FIVE_LINE_SCRIPT = listOf(
            "local x = 1",
            "local y = 2",
            "local t = {a=1,b={c=2}}",
            "local sum = x + y",
            "return sum",
        ).joinToString("\n")

        val WRITE_SCRIPT = listOf(
            "redis.call('set', '$ROLLBACK_KEY', 'fromscript')",
            "return 1",
        ).joinToString("\n")
    }
}
