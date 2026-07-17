package net.internetisalie.lunar.rocks.browser

/** One entry scraped from a luarocks.org stats ranking table (ROCKS-16-15, design §3.3a). */
data class PopularEntry(val name: String, val count: String?)

/**
 * Pure parser for the luarocks.org `/stats/this-week` (and `/stats/dependencies`) ranking tables
 * (ROCKS-16-15, design §3.3a). Derives each package name from the row's `/modules/<author>/<name>`
 * link — the stable per-package key — preserving rank order and capturing the count column.
 *
 * Robust by contract: malformed/blank rows are skipped, never thrown; a page with no recognizable
 * rows yields an empty list so the caller falls back to the neutral prompt (never an error state).
 */
object PopularListParser {

    private val MODULE_LINK = Regex("/modules/[^/\"']+/([^/\"'?<>]+)")
    private val ROW = Regex("(?is)<tr\\b.*?</tr>")
    private val COUNT_CELL = Regex("(?is)<td[^>]*>\\s*([\\d,]+)\\s*</td>")

    /** Parses [html] into ordered [PopularEntry]s; returns an empty list on any unrecognized input. */
    fun parse(html: String): List<PopularEntry> {
        val seen = LinkedHashSet<String>()
        return ROW.findAll(html)
            .mapNotNull { entryOf(it.value) }
            .filter { seen.add(it.name) }
            .toList()
    }

    private fun entryOf(rowHtml: String): PopularEntry? {
        val name = MODULE_LINK.find(rowHtml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val count = COUNT_CELL.find(rowHtml)?.groupValues?.get(1)?.trim()
        return PopularEntry(name, count)
    }
}
