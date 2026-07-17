package net.internetisalie.lunar.rocks.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TC-ROCKS-16-15b: [LuaRocksPopularService] degrades to an empty list (never throws) on a failed or
 * unparseable fetch — the caller then shows the neutral zero-query prompt, NOT the ROCKS-16-05 error
 * state. Also verifies the success and TTL-cache paths with a stubbed fetcher (no network).
 */
class LuaRocksPopularServiceTest {

    @Test
    fun `TC-ROCKS-16-15b a null (non-200) fetch yields an empty list`() {
        val service = LuaRocksPopularService { null }
        assertTrue(service.fetch(nowMs = 0L).isEmpty())
    }

    @Test
    fun `TC-ROCKS-16-15b unparseable HTML yields an empty list`() {
        val service = LuaRocksPopularService { "<html>garbage, no module links</html>" }
        assertTrue(service.fetch(nowMs = 0L).isEmpty())
    }

    @Test
    fun `parses a successful fetch into ranked entries`() {
        val html = """
            <table class="table">
              <tr><td><a href="/modules/a/inspect">inspect</a></td><td>100</td></tr>
              <tr><td><a href="/modules/b/lpeg">lpeg</a></td><td>90</td></tr>
            </table>
        """.trimIndent()
        val service = LuaRocksPopularService { html }
        assertEquals(listOf("inspect", "lpeg"), service.fetch(nowMs = 0L).map { it.name })
    }

    @Test
    fun `serves the cached list within the TTL and does not refetch`() {
        var calls = 0
        val service = LuaRocksPopularService {
            calls += 1
            "<table class=\"table\"><tr><td><a href=\"/modules/a/inspect\">inspect</a></td><td>1</td></tr></table>"
        }
        service.fetch(nowMs = 0L)
        service.fetch(nowMs = 1_000L)
        assertEquals("second call within TTL must be served from cache", 1, calls)
    }
}
