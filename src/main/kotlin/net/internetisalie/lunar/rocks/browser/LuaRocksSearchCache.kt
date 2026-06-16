package net.internetisalie.lunar.rocks.browser

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory TTL cache for `luarocks search` results (ROCKS-02-05 / ROCKS-02-07).
 *
 * Entries expire after [TTL_MS] milliseconds. `nowMs` is supplied by callers
 * (`System.currentTimeMillis()`) so tests can inject a fixed clock without mocking.
 *
 * [invalidateAll] is called by [LuaRocksActionHandler] after a successful install/uninstall,
 * and by the Refresh toolbar action, so that subsequent searches reflect updated state.
 */
object LuaRocksSearchCache {
    const val TTL_MS: Long = 300_000L

    data class Entry(val results: List<LuaRockPackage>, val storedAtMs: Long)

    private val store = ConcurrentHashMap<String, Entry>()

    /**
     * Returns cached results for [query] if fresh (age ≤ [TTL_MS]); null otherwise.
     */
    fun get(query: String, nowMs: Long): List<LuaRockPackage>? {
        val entry = store[query] ?: return null
        return if (nowMs - entry.storedAtMs > TTL_MS) null else entry.results
    }

    /** Stores [results] under [query], tagged with [nowMs]. */
    fun put(query: String, results: List<LuaRockPackage>, nowMs: Long) {
        store[query] = Entry(results, nowMs)
    }

    /** Clears all cached entries. Call after install/uninstall or on manual refresh. */
    fun invalidateAll() {
        store.clear()
    }
}
