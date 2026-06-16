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
        LuaRocksSearchCache.put("inspect", results, t0)

        val got = LuaRocksSearchCache.get("inspect", t0 + 299_000)
        assertEquals(results, got)
    }

    @Test
    fun `TC-ROCKS-02-06 returns null after TTL expires`() {
        val t0 = 1_000_000L
        LuaRocksSearchCache.put("inspect", listOf(pkg("inspect")), t0)

        val got = LuaRocksSearchCache.get("inspect", t0 + 301_000)
        assertNull(got)
    }

    @Test
    fun `returns null for unknown query`() {
        val got = LuaRocksSearchCache.get("nosuchpackage", System.currentTimeMillis())
        assertNull(got)
    }

    @Test
    fun `invalidateAll clears all entries`() {
        val t0 = System.currentTimeMillis()
        LuaRocksSearchCache.put("a", listOf(pkg("a")), t0)
        LuaRocksSearchCache.put("b", listOf(pkg("b")), t0)
        LuaRocksSearchCache.invalidateAll()

        assertNull(LuaRocksSearchCache.get("a", t0))
        assertNull(LuaRocksSearchCache.get("b", t0))
    }

    @Test
    fun `put overwrites previous entry for same query`() {
        val t0 = 1_000_000L
        LuaRocksSearchCache.put("q", listOf(pkg("old")), t0)
        LuaRocksSearchCache.put("q", listOf(pkg("new")), t0 + 1_000)

        val got = LuaRocksSearchCache.get("q", t0 + 2_000)!!
        assertEquals("new", got[0].name)
    }

    @Test
    fun `TTL boundary at exactly TTL_MS is stale`() {
        val t0 = 1_000_000L
        LuaRocksSearchCache.put("q", listOf(pkg("p")), t0)

        // nowMs - storedAtMs == TTL_MS: not >, so NOT stale
        val exactBoundary = LuaRocksSearchCache.get("q", t0 + LuaRocksSearchCache.TTL_MS)
        assertEquals(listOf(pkg("p")), exactBoundary)

        // nowMs - storedAtMs == TTL_MS + 1: stale
        val justOver = LuaRocksSearchCache.get("q", t0 + LuaRocksSearchCache.TTL_MS + 1)
        assertNull(justOver)
    }
}
