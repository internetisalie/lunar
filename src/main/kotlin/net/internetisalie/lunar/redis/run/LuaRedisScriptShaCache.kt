package net.internetisalie.lunar.redis.run

import java.util.concurrent.ConcurrentHashMap

/**
 * Caches loaded script SHAs per `(connectionId, sha)` for EVALSHA mode (design §3.8).
 *
 * A SHA is present in the cache once the server has confirmed `SCRIPT LOAD` for that connection. On
 * a `NOSCRIPT` reply the executor evicts and re-loads (design §3.8, TC-SHA-1). Thread-safe: the
 * executor runs off the EDT and the cache is a light process-lifetime singleton keyed by both the
 * connection id and the content SHA, so two connections never share a stale entry.
 */
object LuaRedisScriptShaCache {

    private val loaded = ConcurrentHashMap.newKeySet<String>()

    private fun keyOf(connectionId: String, sha: String): String = "$connectionId::$sha"

    /** True when [sha] is known to be loaded on the server behind [connectionId]. */
    fun isLoaded(connectionId: String, sha: String): Boolean = loaded.contains(keyOf(connectionId, sha))

    /** Marks [sha] as loaded on the server behind [connectionId] (after a successful `SCRIPT LOAD`). */
    fun markLoaded(connectionId: String, sha: String) {
        loaded.add(keyOf(connectionId, sha))
    }

    /** Evicts [sha] for [connectionId] (on `NOSCRIPT`, before a re-`SCRIPT LOAD`). */
    fun evict(connectionId: String, sha: String) {
        loaded.remove(keyOf(connectionId, sha))
    }
}
