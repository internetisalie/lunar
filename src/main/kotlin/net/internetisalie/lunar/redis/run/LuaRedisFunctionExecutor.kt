package net.internetisalie.lunar.redis.run

import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespValue

/**
 * Executes a Redis Function library deployment and optional FCALL invocation (REDIS-05 design §2.5, §3.5).
 *
 * Command-selection table (design §3.5):
 * - `deployOnly=false, readOnly=false` → `FUNCTION LOAD [REPLACE] <body>` ; `FCALL <fn> <#keys> …`
 * - `deployOnly=false, readOnly=true`  → `FUNCTION LOAD [REPLACE] <body>` ; `FCALL_RO <fn> <#keys> …`
 * - `deployOnly=true`                  → `FUNCTION LOAD [REPLACE] <body>` only (TC-DEPLOY-1)
 *
 * All calls are `suspend` and run on the caller's session coroutine off the EDT
 * (engineering-contract §1, §2). No retained heavy refs; decomposed into ≤30-line helpers (§3).
 */
class LuaRedisFunctionExecutor {

    /**
     * Deploys [body] via `FUNCTION LOAD [REPLACE]`, then optionally invokes the named function
     * via `FCALL`/`FCALL_RO` based on [config] settings (design §3.5).
     *
     * Returns the LOAD reply on deploy-only or on LOAD error; otherwise returns the FCALL reply.
     * A server-side write error under `FCALL_RO` is surfaced verbatim — not blocked client-side
     * (TC-RO-1).
     */
    suspend fun execute(client: RespClient, config: LuaRedisRunConfiguration, body: String): RespValue {
        val loadReply = load(client, body, config.replaceOnLoad)
        if (loadReply is RespValue.Error) return loadReply
        if (config.deployOnly) return loadReply
        return invoke(client, config)
    }

    private suspend fun load(client: RespClient, body: String, replace: Boolean): RespValue {
        val args = buildLoadArgs(body, replace)
        return client.command(args)
    }

    private fun buildLoadArgs(body: String, replace: Boolean): List<ByteArray> {
        val parts = mutableListOf("FUNCTION", "LOAD")
        if (replace) parts.add("REPLACE")
        parts.add(body)
        return parts.map { it.toByteArray(Charsets.UTF_8) }
    }

    private suspend fun invoke(client: RespClient, config: LuaRedisRunConfiguration): RespValue {
        val args = buildCallArgs(config)
        return client.command(args)
    }

    private fun buildCallArgs(config: LuaRedisRunConfiguration): List<ByteArray> {
        val verb = if (config.readOnly) "FCALL_RO" else "FCALL"
        val name = config.functionName.orEmpty()
        val numkeys = config.keys.size.toString()
        val parts = mutableListOf(verb, name, numkeys)
        parts.addAll(config.keys)
        parts.addAll(config.argv)
        return parts.map { it.toByteArray(Charsets.UTF_8) }
    }
}
