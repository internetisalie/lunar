package net.internetisalie.lunar.rocks.browser

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory TTL cache for `luarocks search` results (ROCKS-02-05 / ROCKS-02-07).
 *
 * Entries expire after [TTL_MS] milliseconds. `nowMs` is supplied by callers
 * (`System.currentTimeMillis()`) so tests can inject a fixed clock without mocking.
 *
 * The cache key combines the **resolved registry server** with the query (review finding #70):
 * results from one server never leak to another, so changing the server setting yields fresh
 * results without an explicit invalidate. [invalidateAll] is still called by [LuaRocksInstallExecutor]
 * after a successful install/uninstall (so installed-✓ cross-refs refresh) and by the Refresh action.
 */
object LuaRocksSearchCache {
    const val TTL_MS: Long = 300_000L

    data class Entry(val results: List<LuaRockPackage>, val storedAtMs: Long)

    /** A cache key scoped to the resolved server; `null` server (luarocks default) is a distinct key. */
    private data class Key(val server: String?, val query: String)

    private val store = ConcurrentHashMap<Key, Entry>()

    /**
     * Returns cached results for [query] under [server] if fresh (age ≤ [TTL_MS]); null otherwise.
     */
    fun get(query: String, server: String?, nowMs: Long): List<LuaRockPackage>? {
        val entry = store[Key(server, query)] ?: return null
        return if (nowMs - entry.storedAtMs > TTL_MS) null else entry.results
    }

    /** Stores [results] for [query] under [server], tagged with [nowMs]. */
    fun put(query: String, server: String?, results: List<LuaRockPackage>, nowMs: Long) {
        store[Key(server, query)] = Entry(results, nowMs)
    }

    /** Clears all cached entries. Call after install/uninstall or on manual refresh. */
    fun invalidateAll() {
        store.clear()
    }
}
