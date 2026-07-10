package net.internetisalie.lunar.redis.run

import net.internetisalie.lunar.redis.resp.RespEndpoint
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * An in-process RESP server that replies with a fixed, ordered script of raw byte responses — one per
 * inbound command frame — for [net.internetisalie.lunar.redis.run.LuaRedisScriptExecutor] unit tests.
 *
 * It does not parse commands; it simply pops the next scripted reply each time a request arrives. The
 * first reply is consumed by [net.internetisalie.lunar.redis.resp.RespClient]'s no-auth `HELLO 3`
 * handshake, so tests prepend a handshake reply before the executor's own replies. Runs the
 * accept/serve loop on a daemon thread; the recorded [requests] let a test assert which commands were
 * (not) sent (e.g. TC-RO-1: no `EVAL_RO` after the version gate rejects).
 */
class ScriptedRespServer(private val replies: List<ByteArray>) : Closeable {

    private val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val recordedRequests = mutableListOf<String>()

    init {
        thread(isDaemon = true, name = "scripted-resp-server") { serve() }
    }

    /** The endpoint a [net.internetisalie.lunar.redis.resp.RespClient] opens against this server. */
    fun endpoint(): RespEndpoint = RespEndpoint(host = "127.0.0.1", port = serverSocket.localPort)

    /** Snapshot of the raw request frames the server received, in order. */
    val requests: List<String> get() = synchronized(recordedRequests) { recordedRequests.toList() }

    private fun serve() {
        try {
            serverSocket.accept().use { client ->
                val input = client.getInputStream()
                val output = client.getOutputStream()
                for (reply in replies) {
                    val request = readFrame(input) ?: break
                    synchronized(recordedRequests) { recordedRequests.add(request) }
                    output.write(reply)
                    output.flush()
                }
                blockUntilClosed()
            }
        } catch (_: Exception) {
        }
    }

    /** Reads one RESP array-of-bulk command frame (the encoding the client always sends). */
    private fun readFrame(input: InputStream): String? {
        val builder = StringBuilder()
        val first = input.read()
        if (first < 0) return null
        builder.append(first.toChar())
        val argCount = readLineInto(input, builder).trim().toIntOrNull() ?: return builder.toString()
        repeat(argCount) {
            readBulk(input, builder)
        }
        return builder.toString()
    }

    private fun readBulk(input: InputStream, builder: StringBuilder) {
        val marker = input.read()
        if (marker < 0) return
        builder.append(marker.toChar())
        val length = readLineInto(input, builder).trim().toIntOrNull() ?: return
        val payload = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(payload, read, length - read)
            if (n < 0) break
            read += n
        }
        builder.append(String(payload, Charsets.UTF_8))
        input.read(); input.read() // trailing CRLF
        builder.append("\r\n")
    }

    private fun readLineInto(input: InputStream, builder: StringBuilder): String {
        val line = StringBuilder()
        while (true) {
            val ch = input.read()
            if (ch < 0) break
            if (ch == '\r'.code) {
                input.read() // consume '\n'
                break
            }
            line.append(ch.toChar())
        }
        builder.append(line).append("\r\n")
        return line.toString()
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

    companion object {
        /** RESP3 `%1` map reply to the no-auth `HELLO 3` handshake: `{ server: redis }`. */
        val HELLO_MAP_REPLY: ByteArray = "%1\r\n\$6\r\nserver\r\n\$5\r\nredis\r\n".toByteArray(Charsets.UTF_8)
    }
}
