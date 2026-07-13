package net.internetisalie.lunar.redis.resp

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Socket-level coverage of [RespClient] against a canned in-process server (design §2.3, §3.1, §6).
 *
 * Drives `open`/`command` through a real loopback [ServerSocket] that replies with canned RESP bytes
 * (happy path) or stalls (timeout/cancellation). Covers TC-TIMEOUT-1 (a stalled read past `readMs` →
 * [RespException.Timeout], never a raw `SocketTimeoutException`) and TC-CANCEL-1 (a cancelled
 * [com.intellij.openapi.progress.ProgressIndicator] aborts the in-flight read with
 * [ProcessCanceledException] without completing it). A light platform fixture is used only so the
 * platform cancellation machinery (`ProgressIndicator.checkCanceled`) is initialised; integration
 * against real servers is Phase 6.
 */
class TestRespClient : BasePlatformTestCase() {

    private val servers = mutableListOf<CannedServer>()

    override fun tearDown() {
        try {
            servers.forEach { it.close() }
        } finally {
            super.tearDown()
        }
    }

    /** A RESP3 `HELLO 3` handshake decodes the server's `%…` map reply and selects RESP3. */
    fun testOpenNegotiatesResp3FromHelloMapReply() {
        val server = cannedServer(reply = HELLO_MAP_REPLY)
        val client = runBlocking { RespClient.open(server.endpoint()) }
        try {
            assertEquals(RespProtocol.RESP3, client.protocol)
        } finally {
            client.dispose()
        }
    }

    /** After the handshake, a `command()` writes the request and decodes the canned reply. */
    fun testCommandDecodesCannedReply() {
        val server = cannedServer(reply = HELLO_MAP_REPLY + "+PONG\r\n".toByteArray(Charsets.UTF_8))
        val reply = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                client.command("PING")
            } finally {
                client.dispose()
            }
        }
        assertEquals(RespValue.Simple("PONG"), reply)
    }

    /**
     * REDIS-02 A1: [RespClient.readReply] reads the next reply block **without** sending a command —
     * the server pushes two blocks after the handshake and the client drains the second via `readReply`.
     */
    fun testReadReplyReadsNextBlockWithoutSending() {
        val pushed = HELLO_MAP_REPLY +
            "+FIRST\r\n".toByteArray(Charsets.UTF_8) +
            "+SECOND\r\n".toByteArray(Charsets.UTF_8)
        val server = cannedServer(reply = pushed)
        val replies = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                val first = client.command("PING")
                val second = client.readReply()
                first to second
            } finally {
                client.dispose()
            }
        }
        assertEquals(RespValue.Simple("FIRST"), replies.first)
        assertEquals(RespValue.Simple("SECOND"), replies.second)
    }

    /** REDIS-02 A1: a cancelled indicator aborts an in-flight [RespClient.readReply] (no `!!`, cancellable). */
    fun testReadReplyIsCancellable() {
        val server = replyThenStallServer(handshakeReply = HELLO_MAP_REPLY)
        val indicator = EmptyProgressIndicator()
        val failure = runBlocking {
            val client = RespClient.open(
                server.endpoint(),
                timeouts = RespTimeouts(connectMs = 2_000, readMs = 5_000),
                indicator = indicator,
            )
            try {
                indicator.cancel()
                runCatching { client.readReply() }.exceptionOrNull()
            } finally {
                client.dispose()
            }
        }
        assertTrue("expected ProcessCanceledException, got $failure", failure is ProcessCanceledException)
    }

    /** TC-TIMEOUT-1: a server that never replies makes the read exceed `readMs` → [RespException.Timeout]. */
    fun testReadTimeoutSurfacesAsRespTimeout() {
        val server = stallingServer()
        val failure = runBlocking {
            runCatching {
                RespClient.open(server.endpoint(), timeouts = RespTimeouts(connectMs = 2_000, readMs = 120))
            }.exceptionOrNull()
        }
        assertTrue("expected RespException.Timeout, got $failure", failure is RespException.Timeout)
    }

    /** TC-CANCEL-1: a cancelled indicator aborts the in-flight handshake read with [ProcessCanceledException]. */
    fun testCancelledIndicatorAbortsHandshakeRead() {
        val server = stallingServer()
        val indicator = EmptyProgressIndicator().apply { cancel() }
        val failure = runBlocking {
            runCatching {
                RespClient.open(
                    server.endpoint(),
                    timeouts = RespTimeouts(connectMs = 2_000, readMs = 5_000),
                    indicator = indicator,
                )
            }.exceptionOrNull()
        }
        assertTrue("expected ProcessCanceledException, got $failure", failure is ProcessCanceledException)
    }

    /**
     * TC-CANCEL-1 (command path): a live client whose read stalls aborts with [ProcessCanceledException]
     * when its indicator is cancelled — the read does not complete.
     */
    fun testCancelledIndicatorAbortsCommandRead() {
        val server = replyThenStallServer(handshakeReply = HELLO_MAP_REPLY)
        val indicator = EmptyProgressIndicator()
        val failure = runBlocking {
            val client = RespClient.open(
                server.endpoint(),
                timeouts = RespTimeouts(connectMs = 2_000, readMs = 5_000),
                indicator = indicator,
            )
            try {
                indicator.cancel()
                runCatching { client.command("PING") }.exceptionOrNull()
            } finally {
                client.dispose()
            }
        }
        assertTrue("expected ProcessCanceledException, got $failure", failure is ProcessCanceledException)
    }

    /** Replies with [reply] once the client sends a request, then holds the connection open. */
    private fun cannedServer(reply: ByteArray): CannedServer =
        register(CannedServer(afterRequestReply = reply))

    /** Never replies — the client's read blocks until `readMs` elapses or the indicator cancels. */
    private fun stallingServer(): CannedServer =
        register(CannedServer(afterRequestReply = ByteArray(0)))

    /** Completes the handshake, then leaves later commands unanswered (a stalled command read). */
    private fun replyThenStallServer(handshakeReply: ByteArray): CannedServer =
        register(CannedServer(afterRequestReply = handshakeReply))

    private fun register(server: CannedServer): CannedServer {
        servers.add(server)
        return server
    }

    /**
     * An in-process server that accepts one client, replies with [afterRequestReply] once a request
     * byte arrives, and then holds the connection open until the test closes it — so a stalled read
     * blocks (never reset) until `readMs` elapses or the indicator cancels. Runs the accept/serve on
     * a daemon thread; closing the server unblocks the socket.
     */
    private class CannedServer(
        private val afterRequestReply: ByteArray,
    ) : Closeable {

        private val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())

        init {
            thread(isDaemon = true, name = "canned-resp-server") { serve() }
        }

        fun endpoint(): RespEndpoint =
            RespEndpoint(host = "127.0.0.1", port = serverSocket.localPort)

        private fun serve() {
            try {
                serverSocket.accept().use { client ->
                    awaitFirstByte(client.getInputStream())
                    if (afterRequestReply.isNotEmpty()) {
                        client.getOutputStream().apply {
                            write(afterRequestReply)
                            flush()
                        }
                    }
                    blockUntilClosed()
                }
            } catch (_: Exception) {
            }
        }

        private fun awaitFirstByte(stream: InputStream) {
            stream.read()
        }

        private fun blockUntilClosed() {
            while (!serverSocket.isClosed) {
                Thread.sleep(50)
            }
        }

        override fun close() {
            try {
                serverSocket.close()
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        /** RESP3 `%1` map reply to `HELLO 3`: `{ server: redis }` — enough for RESP3 selection. */
        val HELLO_MAP_REPLY: ByteArray =
            "%1\r\n\$6\r\nserver\r\n\$5\r\nredis\r\n".toByteArray(Charsets.UTF_8)
    }
}
