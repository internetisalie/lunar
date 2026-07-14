package net.internetisalie.lunar.redis.run

import net.internetisalie.lunar.redis.connection.RespServerInfo
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespException
import net.internetisalie.lunar.redis.resp.RespValue
import net.internetisalie.lunar.toolchain.model.SemanticVersion
import java.security.MessageDigest

/**
 * Immutable execution context for [LuaRedisScriptExecutor] (engineering-contract §3, ≤3-arg cap).
 *
 * Bundles the per-run inputs so [LuaRedisScriptExecutor.execute] stays within the argument cap. The
 * [connectionId] keys the [LuaRedisScriptShaCache]; [execMode]/[readOnly] drive the command-selection
 * table (design §3.8); [keys]/[argv] are the `EVAL` KEYS/ARGV.
 */
data class LuaRedisExecContext(
    val connectionId: String,
    val execMode: LuaRedisExecMode,
    val readOnly: Boolean,
    val keys: List<String>,
    val argv: List<String>,
)

/**
 * Runs a script against a [RespClient] per the run configuration's mode (design §3.8).
 *
 * Realizes the EVAL / EVALSHA / `_RO` command-selection table, SHA1 hashing, `SCRIPT LOAD`, the
 * single `NOSCRIPT` retry, and the read-only Redis-<7 fail-fast version gate. All calls are `suspend`
 * and run on the caller's session coroutine off the EDT (engineering-contract §1, §2); the network
 * `command()` calls suspend and honour cancellation inside [RespClient]. Decomposed into ≤30-line
 * helpers (engineering-contract §3).
 */
class LuaRedisScriptExecutor(private val cache: LuaRedisScriptShaCache = LuaRedisScriptShaCache) {

    /** Executes [scriptBody] against [client] under [context], returning the decoded reply (design §3.8). */
    suspend fun execute(client: RespClient, context: LuaRedisExecContext, scriptBody: String): RespValue {
        if (context.readOnly) enforceReadOnlyVersionGate(client)
        return when (context.execMode) {
            LuaRedisExecMode.EVAL -> runEval(client, context, scriptBody)
            LuaRedisExecMode.EVALSHA -> runEvalSha(client, context, scriptBody)
            LuaRedisExecMode.FCALL ->
                error("FCALL mode is routed via LuaRedisFunctionExecutor before reaching LuaRedisScriptExecutor")
        }
    }

    private suspend fun runEval(client: RespClient, context: LuaRedisExecContext, scriptBody: String): RespValue {
        val command = evalCommand(context.readOnly)
        return client.command(evalArgs(command, scriptBody, context))
    }

    private suspend fun runEvalSha(client: RespClient, context: LuaRedisExecContext, scriptBody: String): RespValue {
        val sha = sha1Hex(scriptBody)
        ensureLoaded(client, context.connectionId, sha, scriptBody)
        val command = evalShaCommand(context.readOnly)
        val reply = client.command(evalArgs(command, sha, context))
        if (isNoScript(reply)) return retryAfterReload(client, context, scriptBody, sha)
        return reply
    }

    private suspend fun retryAfterReload(
        client: RespClient,
        context: LuaRedisExecContext,
        scriptBody: String,
        sha: String,
    ): RespValue {
        cache.evict(context.connectionId, sha)
        loadScript(client, context.connectionId, sha, scriptBody)
        return client.command(evalArgs(evalShaCommand(context.readOnly), sha, context))
    }

    private suspend fun ensureLoaded(client: RespClient, connectionId: String, sha: String, scriptBody: String) {
        if (!cache.isLoaded(connectionId, sha)) loadScript(client, connectionId, sha, scriptBody)
    }

    private suspend fun loadScript(client: RespClient, connectionId: String, sha: String, scriptBody: String) {
        val reply = client.command("SCRIPT", "LOAD", scriptBody)
        if (reply is RespValue.Error) {
            throw RespException.Protocol("SCRIPT LOAD failed: ${reply.klass} ${reply.message}".trim())
        }
        cache.markLoaded(connectionId, sha)
    }

    private suspend fun enforceReadOnlyVersionGate(client: RespClient) {
        val version = readServerVersion(client) ?: throw RespException.ServerVersion(READ_ONLY_FLOOR)
        if (version < MIN_READ_ONLY_VERSION) throw RespException.ServerVersion(READ_ONLY_FLOOR)
    }

    private suspend fun readServerVersion(client: RespClient): SemanticVersion? {
        val reply = client.command("INFO", "server")
        val body = (reply as? RespValue.Bulk)?.asString() ?: return null
        val info = RespServerInfo.parse(body)
        return SemanticVersion.parse(info.version)
    }

    private fun evalArgs(command: String, script: String, context: LuaRedisExecContext): List<ByteArray> {
        val parts = mutableListOf(command, script, context.keys.size.toString())
        parts.addAll(context.keys)
        parts.addAll(context.argv)
        return parts.map { it.toByteArray(Charsets.UTF_8) }
    }

    companion object {
        /** Minimum server version supporting the `_RO` command variants (design §3.8, TC-RO-1). */
        val MIN_READ_ONLY_VERSION: SemanticVersion = SemanticVersion(7, 0, 0)
        const val READ_ONLY_FLOOR: String = "Redis 7 / Valkey"

        fun sha1Hex(scriptBody: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(scriptBody.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun evalCommand(readOnly: Boolean): String = if (readOnly) "EVAL_RO" else "EVAL"

        fun evalShaCommand(readOnly: Boolean): String = if (readOnly) "EVALSHA_RO" else "EVALSHA"

        fun isNoScript(reply: RespValue): Boolean = reply is RespValue.Error && reply.klass == "NOSCRIPT"
    }
}
