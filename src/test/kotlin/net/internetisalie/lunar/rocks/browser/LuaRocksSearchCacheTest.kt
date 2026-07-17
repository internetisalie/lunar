package net.internetisalie.lunar.rocks.browser

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [LuaRocksSearchCache] TTL semantics (TC-ROCKS-02-06 from requirements).
 *
 * Fully headless — no IDE platform required.
 */
class LuaRocksSearchCacheTest {

    @After
    fun tearDown() {
        LuaRocksSearchCache.invalidateAll()
    }

    private fun pkg(name: String) = LuaRockPackage(name, "1.0-1", "rockspec", "https://luarocks.org", "")

    @Test
    fun `TC-ROCKS-02-06 returns cached result within TTL`() {
        val t0 = 1_000_000L
        val results = listOf(pkg("inspect"))
        LuaRocksSearchCache.put("inspect", null, results, t0)

        val got = LuaRocksSearchCache.get("inspect", null, t0 + 299_000)
        assertEquals(results, got)
    }

    @Test
    fun `TC-ROCKS-02-06 returns null after TTL expires`() {
        val t0 = 1_000_000L
        LuaRocksSearchCache.put("inspect", null, listOf(pkg("inspect")), t0)

        val got = LuaRocksSearchCache.get("inspect", null, t0 + 301_000)
        assertNull(got)
    }

    @Test
    fun `returns null for unknown query`() {
        val got = LuaRocksSearchCache.get("nosuchpackage", null, System.currentTimeMillis())
        assertNull(got)
    }

    @Test
    fun `invalidateAll clears all entries`() {
        val t0 = System.currentTimeMillis()
        LuaRocksSearchCache.put("a", null, listOf(pkg("a")), t0)
        LuaRocksSearchCache.put("b", null, listOf(pkg("b")), t0)
        LuaRocksSearchCache.invalidateAll()

        assertNull(LuaRocksSearchCache.get("a", null, t0))
        assertNull(LuaRocksSearchCache.get("b", null, t0))
    }

    @Test
    fun `put overwrites previous entry for same query`() {
        val t0 = 1_000_000L
        LuaRocksSearchCache.put("q", null, listOf(pkg("old")), t0)
        LuaRocksSearchCache.put("q", null, listOf(pkg("new")), t0 + 1_000)

        val got = LuaRocksSearchCache.get("q", null, t0 + 2_000)
        assertEquals("new", got?.get(0)?.name)
    }

    @Test
    fun `TTL boundary at exactly TTL_MS is stale`() {
        val t0 = 1_000_000L
        LuaRocksSearchCache.put("q", null, listOf(pkg("p")), t0)

        // nowMs - storedAtMs == TTL_MS: not >, so NOT stale
        val exactBoundary = LuaRocksSearchCache.get("q", null, t0 + LuaRocksSearchCache.TTL_MS)
        assertEquals(listOf(pkg("p")), exactBoundary)

        // nowMs - storedAtMs == TTL_MS + 1: stale
        val justOver = LuaRocksSearchCache.get("q", null, t0 + LuaRocksSearchCache.TTL_MS + 1)
        assertNull(justOver)
    }

    @Test
    fun `cache key is scoped to the resolved server (finding #70)`() {
        val t0 = 1_000_000L
        LuaRocksSearchCache.put("inspect", "https://a.example", listOf(pkg("fromA")), t0)

        // A different server (including the null default) must not read server A's entry.
        assertNull(LuaRocksSearchCache.get("inspect", "https://b.example", t0))
        assertNull(LuaRocksSearchCache.get("inspect", null, t0))
        assertEquals("fromA", LuaRocksSearchCache.get("inspect", "https://a.example", t0)?.get(0)?.name)
    }
}
