package net.internetisalie.lunar.redis.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * One readiness attempt against a just-launched server (BUG-446).
 *
 * Separated from [LuaRedisServerReadiness] so the polling policy can be tested without a socket and
 * the socket work can be replaced in tests without a server.
 */
internal fun interface LuaRedisProbe {
    /** True when the server sent **any** reply — see [LuaRedisServerReadiness] for why any. */
    fun answers(
        host: String,
        port: Int,
    ): Boolean
}

/**
 * BUG-446 — **a launched server is not ready when its process is; wait for it to answer.**
 *
 * `LuaRedisServerLauncher` used to return as soon as the process had been spawned, and the run then
 * opened a [net.internetisalie.lunar.redis.resp.RespClient] against a server that was not yet
 * listening. Measured on the builder, three runs per path, neither was ever ready at that moment
 * (bug-report §5b):
 *
 * | path | at spawn | answers after |
 * | :-- | :-- | :-- |
 * | Docker `redis:8` | connected, **no reply** — 3/3 | 31 / 36 / 70 ms |
 * | `redis-server --port N --save ""` | connection **refused** — 3/3 | 15 / 17 / 32 ms |
 *
 * **The check is protocol-level, and that is the whole point.** A TCP connect is not a readiness
 * signal for a container: Docker publishes a port by binding it on the host at *create* time, so the
 * connect succeeds against docker-proxy while nothing is listening inside — the Docker row above is
 * that false positive, measured. Testcontainers works around the same trap by running its port check
 * *inside* the container (`HostPortWaitStrategy` execs `nc -z`), which needs a tool present in the
 * image and cannot cover a local binary at all. Asking the server to speak covers both paths with
 * one mechanism and no assumptions about the image.
 *
 * **Any reply counts, not specifically `PONG`.** A server answering `-NOAUTH Authentication
 * required` is listening, which is the only question being asked here; authentication belongs to the
 * client that follows. Requiring `PONG` would turn a password-protected server into a launch timeout.
 *
 * Suspending, and polling with [delay] rather than `Thread.sleep`, so cancelling the run
 * configuration abandons the wait instead of pinning a thread for the full timeout
 * (engineering-contract CANCELLATION EXHAUSTIVENESS).
 */
internal class LuaRedisServerReadiness(
    private val probe: LuaRedisProbe = SocketProbe,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    /** Polls until the server answers, returning false if [timeoutMs] elapses first. */
    suspend fun awaitReply(
        host: String,
        port: Int,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (withContext(Dispatchers.IO) { probe.answers(host, port) }) return true
            if (System.currentTimeMillis() >= deadline) return false
            delay(POLL_INTERVAL_MS)
        }
    }

    /**
     * The real probe: an inline RESP `PING`, and a single byte back is enough.
     *
     * Inline commands (`PING\r\n`) are accepted by both Redis and Valkey, so this needs no RESP
     * encoder and cannot be affected by protocol negotiation — the handshake is the client's job,
     * and running one here would make readiness depend on the thing readiness is guarding.
     */
    private object SocketProbe : LuaRedisProbe {
        override fun answers(
            host: String,
            port: Int,
        ): Boolean =
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = REPLY_TIMEOUT_MS
                    socket.getOutputStream().apply {
                        write(INLINE_PING)
                        flush()
                    }
                    socket.getInputStream().read() >= 0
                }
            } catch (_: IOException) {
                false
            }
    }

    companion object {
        /**
         * Generous against a cold image start (the measured gaps are tens of milliseconds, but a
         * first-run container may have to unpack layers) while still bounded, so a server that never
         * comes up fails the run instead of hanging it.
         */
        const val DEFAULT_TIMEOUT_MS = 15_000L

        private const val POLL_INTERVAL_MS = 25L
        private const val CONNECT_TIMEOUT_MS = 500
        private const val REPLY_TIMEOUT_MS = 1_000
        private val INLINE_PING = "PING\r\n".toByteArray(Charsets.US_ASCII)
    }
}
