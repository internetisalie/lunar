package net.internetisalie.lunar.redis.resp

import com.intellij.openapi.progress.ProgressIndicator
import java.io.IOException
import java.io.OutputStream
import java.io.PushbackInputStream
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Runs the RESP2/RESP3 connection handshake for [RespClient.open] (design §3.1).
 *
 * Operates directly on the freshly-opened [socket] streams — the [RespClient] does not exist until
 * negotiation succeeds. Sends `HELLO 3` (with `AUTH` when credentials are present); a `Map`/`Array`
 * reply selects [RespProtocol.RESP3], any `Error` reply falls back to [RespProtocol.RESP2]. When a
 * non-zero database is requested, issues `SELECT` and requires `+OK`. Reads use the raw stream
 * (explicit UTF-8, no `BufferedReader.readLine`); a [SocketTimeoutException] surfaces as
 * [RespException.Timeout] and cancellation via the [indicator] as `ProcessCanceledException`.
 */
internal class RespHandshake(
    private val socket: Socket,
    private val endpoint: RespEndpoint,
    private val indicator: ProgressIndicator?,
) {

    private val output: OutputStream = socket.getOutputStream()
    private val input: PushbackInputStream =
        PushbackInputStream(CancellationAwareInputStream(socket.getInputStream(), indicator))

    fun negotiate(): RespClient {
        val protocol = negotiateProtocol()
        selectDatabase()
        return RespClient.attach(socket, protocol, indicator)
    }

    private fun negotiateProtocol(): RespProtocol {
        indicator?.checkCanceled()
        return when (exchange(helloArgs())) {
            is RespValue.Map, is RespValue.Array -> RespProtocol.RESP3
            is RespValue.Error -> fallbackToResp2()
            else -> RespProtocol.RESP2
        }
    }

    private fun fallbackToResp2(): RespProtocol {
        val password = endpoint.password
        if (!password.isNullOrEmpty()) {
            val reply = exchange(authArgs(password))
            if (reply is RespValue.Error) throw RespException.Protocol("AUTH rejected: ${reply.klass} ${reply.message}")
        } else {
            exchange(bytesOf("PING"))
        }
        return RespProtocol.RESP2
    }

    private fun selectDatabase() {
        if (endpoint.database == 0) return
        indicator?.checkCanceled()
        val reply = exchange(bytesOf("SELECT", endpoint.database.toString()))
        val accepted = reply is RespValue.Simple && reply.text == "OK"
        if (!accepted) throw RespException.Protocol("SELECT ${endpoint.database} was not accepted: $reply")
    }

    private fun helloArgs(): List<ByteArray> {
        val words = mutableListOf("HELLO", "3")
        val password = endpoint.password
        if (!password.isNullOrEmpty()) {
            words.add("AUTH")
            words.add(endpoint.username ?: "default")
            words.add(password)
        }
        return words.map { it.toByteArray(Charsets.UTF_8) }
    }

    private fun authArgs(password: String): List<ByteArray> {
        val username = endpoint.username
        val words = if (username.isNullOrEmpty()) listOf("AUTH", password) else listOf("AUTH", username, password)
        return words.map { it.toByteArray(Charsets.UTF_8) }
    }

    private fun bytesOf(vararg words: String): List<ByteArray> = words.map { it.toByteArray(Charsets.UTF_8) }

    private fun exchange(args: List<ByteArray>): RespValue {
        indicator?.checkCanceled()
        return try {
            output.write(RespCodec.encodeCommand(args))
            output.flush()
            RespCodec.decode(input)
        } catch (timeout: SocketTimeoutException) {
            throw RespException.Timeout("connect")
        } catch (failure: IOException) {
            throw RespException.Io(failure)
        }
    }
}
