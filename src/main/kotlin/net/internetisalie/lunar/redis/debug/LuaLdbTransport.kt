package net.internetisalie.lunar.redis.debug

import com.intellij.openapi.Disposable
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespValue

/**
 * The LDB request/reply discipline over a connection (design §2.10, §3.1).
 *
 * A narrow seam so [LuaLdbController] depends on the LDB command surface, not a concrete socket
 * client — the production implementation is [LuaLdbTransport] (over a REDIS-01 `RespClient`); unit
 * tests feed a scripted fake (no live socket, contract §5 / TC-LDB-ERR-1..2). Every call suspends and
 * runs off the EDT.
 */
interface LdbIo {

    /** `SCRIPT DEBUG YES|SYNC` — enters the debug session, returning the `+OK` (or `-ERR`) reply. */
    suspend fun enterDebug(mode: LuaRedisDebugMode): RespValue

    /** Send the debugged `EVAL <script> <numkeys> <keys…> <argv…>` — its reply is the first stop block. */
    suspend fun eval(scriptBody: String, keys: List<String>, argv: List<String>): RespValue

    /** Send one LDB command; the reply is its single reply block (design §3.1 step 3). */
    suspend fun send(command: LdbCommand): RespValue

    /**
     * Read the next reply block without sending a command (design §11 amendment A1). After a resuming
     * command whose reply is the `["<endsession>"]` block, the real `EVAL` result / abort error is a
     * separate trailing block on the same connection (confirmed live in Phase 5); this drains it.
     */
    suspend fun readReply(): RespValue
}

/**
 * Owns the [RespClient] for a debug session and speaks LDB over it (design §2.10, §3.1).
 *
 * LDB is synchronous per connection: a command written on the connection is answered by exactly one
 * reply block, so every method is a single `RespClient.command`. [enterDebug] and [eval] frame their
 * argument vectors directly (they are not [LdbCommand]s); all other verbs route through [LdbWire].
 * [Disposable]: [dispose] closes the underlying client.
 */
class LuaLdbTransport(private val client: RespClient) : LdbIo, Disposable {

    override suspend fun enterDebug(mode: LuaRedisDebugMode): RespValue =
        client.command(LdbWire.encode(LdbCommand.EnterDebug(mode)))

    override suspend fun eval(scriptBody: String, keys: List<String>, argv: List<String>): RespValue =
        client.command(evalArgs(scriptBody, keys, argv))

    override suspend fun send(command: LdbCommand): RespValue =
        client.command(LdbWire.encode(command))

    override suspend fun readReply(): RespValue = client.readReply()

    override fun dispose() {
        client.dispose()
    }

    private fun evalArgs(scriptBody: String, keys: List<String>, argv: List<String>): List<ByteArray> {
        val tokens = mutableListOf(EVAL_VERB, scriptBody, keys.size.toString())
        tokens.addAll(keys)
        tokens.addAll(argv)
        return tokens.map { it.toByteArray(Charsets.UTF_8) }
    }

    private companion object {
        const val EVAL_VERB = "EVAL"
    }
}
