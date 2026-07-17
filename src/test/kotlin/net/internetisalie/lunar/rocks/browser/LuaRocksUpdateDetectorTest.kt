package net.internetisalie.lunar.rocks.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TC-ROCKS-16-06: [LuaRocksUpdateDetector.hasUpdate] / [LuaRocksUpdateDetector.latestOf]
 * (design §3.2). Update is reported only when the latest available version is strictly greater.
 */
class LuaRocksUpdateDetectorTest {

    @Test
    fun `TC-ROCKS-16-06 detects a newer version`() {
        assertTrue(LuaRocksUpdateDetector.hasUpdate("3.1.2-0", "3.1.3-0"))
    }

    @Test
    fun `TC-ROCKS-16-06 no update when versions equal`() {
        assertFalse(LuaRocksUpdateDetector.hasUpdate("3.1.3-0", "3.1.3-0"))
    }

    @Test
    fun `TC-ROCKS-16-06 no update when latest is null`() {
        assertFalse(LuaRocksUpdateDetector.hasUpdate("3.1.3-0", null))
    }

    @Test
    fun `no update when installed is newer than latest`() {
        assertFalse(LuaRocksUpdateDetector.hasUpdate("3.1.4-0", "3.1.3-0"))
    }

    @Test
    fun `latestOf returns the max version across rows`() {
        val rows = listOf(
            row("inspect", "3.1.2-0"),
            row("inspect", "3.1.3-0"),
            row("inspect", "3.1.1-0"),
        )
        assertEquals("3.1.3-0", LuaRocksUpdateDetector.latestOf(rows))
    }

    @Test
    fun `latestOf is null for an empty list`() {
        assertEquals(null, LuaRocksUpdateDetector.latestOf(emptyList()))
    }

    private fun row(name: String, version: String) =
        LuaRockRow(LuaRockPackage(name, version, "rockspec", "https://luarocks.org", ""), installed = false)
}
