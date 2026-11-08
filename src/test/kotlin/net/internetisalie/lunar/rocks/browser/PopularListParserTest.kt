package net.internetisalie.lunar.rocks.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TC-ROCKS-16-15a: [PopularListParser] over a static HTML fixture trimmed from
 * `luarocks.org/stats/this-week` as served on 2026-11-08 (rank, downloads, module columns). Package names derive from each `/modules/<author>/<name>` link in
 * rank order; the count column is captured; malformed/blank rows are skipped without throwing.
 */
class PopularListParserTest {
    private fun fixture(): String =
        requireNotNull(javaClass.getResourceAsStream("/rocks/stats-this-week.html")) {
            "missing test fixture /rocks/stats-this-week.html"
        }.reader().use { it.readText() }

    @Test
    fun `TC-ROCKS-16-15a parses ranked entries from the stats table`() {
        val entries = PopularListParser.parse(fixture())

        assertEquals(listOf("luafilesystem", "argparse", "dkjson"), entries.map { it.name })
        assertEquals("the count is the downloads cell, not the rank", "83,158", entries[0].count)
        assertEquals("62,626", entries[1].count)
    }

    @Test
    fun `skips malformed rows and never throws`() {
        val entries = PopularListParser.parse(fixture())
        assertTrue("malformed link-less row must be skipped", entries.none { it.name.contains("malformed") })
    }

    @Test
    fun `empty or unparseable HTML yields an empty list`() {
        assertTrue(PopularListParser.parse("").isEmpty())
        assertTrue(PopularListParser.parse("<html><body>no table here</body></html>").isEmpty())
    }
}
