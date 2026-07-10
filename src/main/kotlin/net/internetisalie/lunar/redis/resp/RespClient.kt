package net.internetisalie.lunar.redis.resp

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.io.PushbackInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.coroutineContext

/** Negotiated RESP reply protocol for a connection (design §2.3). */
enum class RespProtocol { RESP2, RESP3 }

/** Connect/read socket timeouts in milliseconds (design §2.3). */
data class RespTimeouts(val connectMs: Int = 5_000, val readMs: Int = 30_000)

/**
 * Endpoint + handshake inputs for [RespClient.open] (design §3.1).
 *
 * A Phase-2 value object carrying only the primitives the handshake needs — host/port/TLS plus the
 * optional `SELECT` database and AUTH credentials. The connection model and credential store
 * (`LuaRedisServerConnection` / `LuaRedisCredentialStore`, design §2.4/§2.9) are REDIS-01 Phase 3;
 * that phase adds a thin adapter resolving a connection + secret into this object, so Phase 2 does
 * not forward-depend on unbuilt types. Keeps [RespClient.open] within the 3-argument cap.
 */
data class RespEndpoint(
    val host: String,
    val port: Int,
    val tls: Boolean = false,
    val database: Int = 0,
    val username: String? = null,
    val password: String? = null,
)

/**
 * One TCP (optionally TLS) connection to a Redis/Valkey server (design §2.3, §3.1).
 *
 * Sends commands and reads replies over a raw `InputStream`/[PushbackInputStream] — never
 * `BufferedReader.readLine` (risks-and-gaps Risk 1.1 / epic RISK-R09), so [RespCodec]'s
 * length-prefixed loops reassemble fragmented frames with explicit UTF-8. All socket I/O runs on
 * [Dispatchers.IO] off the EDT (engineering-contract THREADING); the reader mirrors the DBGp
 * transport (`run/LuaDebugConnection` `send`/`readLoop`), sharing no code. Cancellation is honoured
 * via [ProgressIndicator.checkCanceled] and coroutine [ensureActive] at the start of every socket
 * read (engineering-contract CANCELLATION EXHAUSTIVENESS); a [SocketTimeoutException] on connect or
 * read surfaces as [RespException.Timeout], never raw.
 */
class RespClient private constructor(
    private val socket: Socket,
    val protocol: RespProtocol,
    private val indicator: ProgressIndicator?,
) : Disposable {

    private val output: OutputStream = socket.getOutputStream()
    private val input: PushbackInputStream =
        PushbackInputStream(CancellationAwareInputStream(socket.getInputStream(), indicator))
    private val commandMutex = Mutex()

    /** Send one command (raw bulk args) and suspend until its reply is decoded (design §3.3). */
    suspend fun command(args: List<ByteArray>): RespValue = commandMutex.withLock {
        exchange(args, op = "command")
    }

    /** UTF-8 convenience overload for [command]. */
    suspend fun command(vararg args: String): RespValue =
        command(args.map { it.toByteArray(Charsets.UTF_8) })

    private suspend fun exchange(args: List<ByteArray>, op: String): RespValue {
        coroutineContext.ensureActive()
        indicator?.checkCanceled()
        return withContext(Dispatchers.IO) {
            try {
                output.write(RespCodec.encodeCommand(args))
                output.flush()
                RespCodec.decode(input)
            } catch (timeout: SocketTimeoutException) {
                throw RespException.Timeout(op)
            } catch (cancelled: ProcessCanceledException) {
                throw cancelled
            } catch (failure: IOException) {
                throw RespException.Io(failure)
            }
        }
    }

    override fun dispose() {
        try {
            socket.close()
        } catch (closeFailure: IOException) {
            log.info("RespClient socket close failed: ${closeFailure.message}")
        }
    }

    companion object {
        private val log = logger<RespClient>()

        /** Open a socket to [endpoint], run the HELLO/AUTH/SELECT handshake, return a live client (design §3.1). */
        suspend fun open(
            endpoint: RespEndpoint,
            timeouts: RespTimeouts = RespTimeouts(),
            indicator: ProgressIndicator? = null,
        ): RespClient = withContext(Dispatchers.IO) {
            indicator?.checkCanceled()
            val socket = connectSocket(endpoint, timeouts, indicator)
            try {
                RespHandshake(socket, endpoint, indicator).negotiate()
            } catch (failure: Throwable) {
                closeQuietly(socket)
                throw failure
            }
        }

        private fun connectSocket(
            endpoint: RespEndpoint,
            timeouts: RespTimeouts,
            indicator: ProgressIndicator?,
        ): Socket {
            indicator?.checkCanceled()
            val socket = if (endpoint.tls) SSLSocketFactory.getDefault().createSocket() else Socket()
            return try {
                socket.connect(InetSocketAddress(endpoint.host, endpoint.port), timeouts.connectMs)
                socket.soTimeout = timeouts.readMs
                socket
            } catch (timeout: SocketTimeoutException) {
                closeQuietly(socket)
                throw RespException.Timeout("connect")
            } catch (failure: IOException) {
                closeQuietly(socket)
                throw RespException.Io(failure)
            }
        }

        private fun closeQuietly(socket: Socket) {
            try {
                socket.close()
            } catch (closeFailure: IOException) {
                log.info("RespClient socket close failed: ${closeFailure.message}")
            }
        }

        /**
         * Build a client around an already-open [socket] with a negotiated [protocol]. Package-visible
         * to [RespHandshake]; exposed as `internal` so the socket + protocol are wired in one place.
         */
        internal fun attach(socket: Socket, protocol: RespProtocol, indicator: ProgressIndicator?): RespClient =
            RespClient(socket, protocol, indicator)
    }
}
