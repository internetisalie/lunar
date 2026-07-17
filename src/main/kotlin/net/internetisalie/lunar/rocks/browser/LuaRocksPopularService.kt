package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.HttpRequests
import java.util.concurrent.atomic.AtomicReference

/** Fetches HTML for a URL; injectable so tests supply a stubbed (or failing) fetcher. */
fun interface PopularHtmlFetcher {
    /** Returns the page body, or `null` on any non-200 / IO failure (never throws). */
    fun fetch(url: String): String?
}

/**
 * Best-effort "Popular / Trending" list scraped from luarocks.org's curated ranking tables
 * (ROCKS-16-15, design §3.3a). TTL-cached (mirrors [LuaRocksSearchCache]); off-EDT fetch with a short
 * timeout. **Any** non-200 / empty / unparseable response yields an empty list — never a throw, never
 * an error state (a missing popular list degrades to the neutral zero-query prompt, not ROCKS-16-05).
 */
class LuaRocksPopularService(private val fetcher: PopularHtmlFetcher = defaultFetcher) {

    private val cache = AtomicReference<Cached?>(null)

    /** Returns the ranked popular entries, or an empty list on any fetch/parse failure. */
    fun fetch(nowMs: Long = System.currentTimeMillis()): List<PopularEntry> {
        cache.get()?.takeIf { nowMs - it.storedAtMs <= TTL_MS }?.let { return it.entries }
        val entries = runCatching { PopularListParser.parse(fetcher.fetch(STATS_THIS_WEEK).orEmpty()) }
            .getOrElse { emptyList() }
        cache.set(Cached(entries, nowMs))
        return entries
    }

    private data class Cached(val entries: List<PopularEntry>, val storedAtMs: Long)

    private companion object {
        const val TTL_MS: Long = 3_600_000L
        const val STATS_THIS_WEEK = "https://luarocks.org/stats/this-week"
        private val log = logger<LuaRocksPopularService>()

        val defaultFetcher = PopularHtmlFetcher { url ->
            runCatching { HttpRequests.request(url).readTimeout(FETCH_TIMEOUT_MS).readString() }
                .onFailure { log.debug("popular list fetch failed for $url: ${it.message}") }
                .getOrNull()
        }
        const val FETCH_TIMEOUT_MS = 5_000
    }
}
