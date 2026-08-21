package net.internetisalie.lunar.redis.connection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * BUG-446 — the readiness gate, and specifically **that a TCP connect is not one**.
 *
 * [testAServerThatAcceptsButNeverRepliesIsNotReady] is the test that matters: it reproduces the
 * docker-proxy shape measured in bug-report §5b, where a published container port is bound on the
 * host at container-*create* time, so a connect succeeds while nothing inside the container is
 * listening. Every "is it up yet?" check built on `Socket.connect` alone returns true there — which
 * is why [LuaRedisServerReadiness] asks the server to speak instead.
 */
class TestLuaRedisServerReadiness : BasePlatformTestCase() {
    /**
     * The trap, reproduced without Docker: a socket that accepts the connection and then says
     * nothing at all. A connect-based check calls this ready; the probe must not.
     *
     * The timeout is deliberately short — this test asserts a *negative*, so it has to give up on
     * its own rather than be rescued by the suite timing out.
     */
    fun testAServerThatAcceptsButNeverRepliesIsNotReady() {
        ServerSocket(0).use { listener ->
            val silent =
                thread(isDaemon = true) {
                    runCatching { listener.accept().use { Thread.sleep(SILENT_HOLD_MS) } }
                }

            val ready =
                runBlocking {
                    LuaRedisServerReadiness(timeoutMs = SHORT_TIMEOUT_MS).awaitReply(LOOPBACK, listener.localPort)
                }

            silent.interrupt()
            assertFalse(
                "a socket that accepts but never answers must NOT count as ready — this is exactly " +
                    "what docker-proxy does for a published port before the container is listening",
                ready,
            )
        }
    }

    /** And the positive: a server that answers anything at all is ready. */
    fun testAServerThatRepliesIsReady() {
        ServerSocket(0).use { listener ->
            val responder =
                thread(isDaemon = true) {
                    runCatching {
                        listener.accept().use { socket ->
                            socket.getInputStream().read()
                            socket.getOutputStream().apply {
                                write("+PONG\r\n".toByteArray(Charsets.US_ASCII))
                                flush()
                            }
                        }
                    }
                }

            val ready =
                runBlocking {
                    LuaRedisServerReadiness(timeoutMs = SHORT_TIMEOUT_MS).awaitReply(LOOPBACK, listener.localPort)
                }

            responder.join(SHORT_TIMEOUT_MS)
            assertTrue("a server that replies must be reported ready", ready)
        }
    }

    /**
     * An error reply still means "listening", which is the only question this gate asks.
     *
     * A password-protected server answers `-NOAUTH Authentication required` to an unauthenticated
     * `PING`. Treating that as not-ready would turn every secured server into a launch timeout, and
     * authentication is the client's business, not the gate's.
     */
    fun testAnErrorReplyStillCountsAsListening() {
        ServerSocket(0).use { listener ->
            thread(isDaemon = true) {
                runCatching {
                    listener.accept().use { socket ->
                        socket.getInputStream().read()
                        socket.getOutputStream().apply {
                            write("-NOAUTH Authentication required\r\n".toByteArray(Charsets.US_ASCII))
                            flush()
                        }
                    }
                }
            }

            val ready =
                runBlocking {
                    LuaRedisServerReadiness(timeoutMs = SHORT_TIMEOUT_MS).awaitReply(LOOPBACK, listener.localPort)
                }

            assertTrue("-NOAUTH is a reply, so the server is listening and the gate must pass", ready)
        }
    }

    /** The polling policy itself, with no socket involved: keep trying until the server comes up. */
    fun testAwaitKeepsPollingUntilTheServerAnswers() {
        val attempts = AtomicInteger()
        val readyOnThirdAttempt = LuaRedisProbe { _, _ -> attempts.incrementAndGet() >= 3 }

        val ready =
            runBlocking {
                LuaRedisServerReadiness(readyOnThirdAttempt, SHORT_TIMEOUT_MS).awaitReply(LOOPBACK, 6379)
            }

        assertTrue("the gate must wait out a slow start rather than fail on the first attempt", ready)
        assertEquals("it should have stopped as soon as the server answered", 3, attempts.get())
    }

    /** And it must give up, so a server that never starts fails the run instead of hanging it. */
    fun testAwaitGivesUpAfterTheTimeout() {
        val never = LuaRedisProbe { _, _ -> false }

        val ready =
            runBlocking { LuaRedisServerReadiness(never, timeoutMs = 100L).awaitReply(LOOPBACK, 6379) }

        assertFalse("a server that never answers must time out", ready)
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val SHORT_TIMEOUT_MS = 2_000L
        const val SILENT_HOLD_MS = 5_000L
    }
}
