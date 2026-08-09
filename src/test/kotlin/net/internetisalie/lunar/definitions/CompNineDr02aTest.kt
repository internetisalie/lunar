package net.internetisalie.lunar.definitions

/**
 * THROWAWAY — COMP-09 DR-02a, which `risks-and-gaps.md` labelled "**blocks NFR-1**".
 *
 * The problem it names: `completeBasic()` returns only when completion has finished, so **no
 * time-to-first-result figure existed for any fixture in this plan**, including the ones the design
 * quotes. `non-functional.md` targets time-to-first-result at < 100 ms and COMP-09-08 has to assert
 * against it, so without a first-element observer there was nothing to assert.
 *
 * The observer it built is no longer throwaway: it is [FirstElementProbe] / [TimedCompletionTestCase],
 * promoted in Phase 0 and now carrying COMP-09-08's gate ([MemberEnumerationLatencyGateTest]). What
 * remains here is the *record* — the runs design §1.9 quotes — kept until Phase 5 re-measures.
 *
 * DR-02 had already argued time-to-first == time-to-exhaustive *by construction*, from reading the
 * emit loop. This session's record on structural claims is poor, so it was measured: the gap is
 * 31 ms of 777 ms.
 */
class CompNineDr02aTest : TimedCompletionTestCase() {
    /** The BUG-429 shape: enough members that a first/exhaustive gap would be visible if one existed. */
    private fun bigLibrary(): String {
        val root = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(3400) { i -> root.append("---@type number\nwx.wxC_$i = nil\n\n") }
        repeat(200) { i -> root.append("---@return boolean\nfunction wx.f$i() end\n\n") }
        root.append("return wx\n")
        return root.toString()
    }

    /** Does the probe see anything at all? Establish the mechanism before quoting a number from it. */
    fun testDr02aProbeSeesResults() {
        installFirstElementProbe()
        registerLibraryRoot(
            mapOf(
                "wx.lua" to
                    """
                    ---@meta

                    ---@class wx
                    wx = {}

                    ---@type number
                    wx.alpha = nil

                    ---@type number
                    wx.beta = nil

                    ---@return boolean
                    function wx.gamma() end

                    return wx
                    """.trimIndent(),
            ),
        )
        val timeline = timeCompletion("wx.<caret>\n")
        println("DR-02a small fixture timeline = $timeline")
        assertNotNull("probe never ran — registration or ordering is wrong, not a measurement", timeline)
        assertTrue("probe ran but saw no results, so it cannot time the first one", (timeline?.count ?: 0) > 0)
    }

    /**
     * Does time-to-first track the receiver's member count? The NFR says time-to-first must be
     * "independent of index size AND candidate count", so this is the clause COMP-09-08 gates.
     *
     * Both receivers must be measured **cold**, and in **separate files** — the per-file snapshot is
     * memoized, so a second receiver in the same file rides the first one's build and would report a
     * flat 0 for reasons that have nothing to do with its member count. An earlier revision of this
     * harness took a median of five, which put the one cold sample under four warm ones and reported
     * 1 ms vs 0 ms: noise, dressed as a verdict.
     */
    fun testDr02aNarrowVersusWideReceiver() {
        installFirstElementProbe()
        val narrowRoot = StringBuilder("---@meta\n\n---@class Narrow\nNarrow = {}\n\n")
        repeat(3) { i -> narrowRoot.append("---@type number\nNarrow.n$i = nil\n\n") }
        registerLibraryRoot(mapOf("wide.lua" to bigLibrary(), "narrow.lua" to narrowRoot.toString()))

        val wide = timeCompletion("wx.<caret>\n")
        val narrow = timeCompletion("Narrow.<caret>\n")
        println("DR-02a WIDE   COLD (3600 members) first=${wide?.firstUs}us count=${wide?.count}")
        println("DR-02a NARROW COLD (3 members)    first=${narrow?.firstUs}us count=${narrow?.count}")
        val wideUs = wide?.firstUs ?: 0
        val narrowUs = narrow?.firstUs ?: 0
        println("DR-02a ratio wide/narrow = ${if (narrowUs > 0) wideUs / narrowUs else -1}x")
        println(
            "DR-02a => time-to-first is " +
                if (narrowUs > 0 &&
                    wideUs > narrowUs * 4
                ) {
                    "DRIVEN BY member count — the NFR's independence clause is violated today"
                } else {
                    "not obviously driven by member count"
                },
        )
    }

    /** The money measurement: is there any gap between the first element and the last? */
    fun testDr02aFirstVersusExhaustive() {
        installFirstElementProbe()
        registerLibraryRoot(mapOf("wx.lua" to bigLibrary()))
        val cold = timeCompletion("wx.<caret>\n")
        println(
            "DR-02a COLD  first=${cold?.firstMs}ms last=${cold?.lastMs}ms count=${cold?.count}  (${cold?.firstUs}us/${cold?.lastUs}us)",
        )

        // Medians of 5 of the SAME completion (DR-08). An earlier revision of this harness varied the
        // prefix per sample — `wx.wxC_1`, `wx.wxC_2`, … — so the five samples matched 1 699, 1 213 and
        // 889 candidates respectively and the "spread" was workload, not noise.
        val samples = (1..5).mapNotNull { timeCompletion("wx.<caret>\n") }
        val firsts = samples.map { it.firstUs }.sorted()
        val lasts = samples.map { it.lastUs }.sorted()
        println(
            "DR-02a WARM (same completion x5) firsts(us)=$firsts lasts(us)=$lasts counts=${samples.map { it.count }}",
        )
        println("DR-02a WARM medians: first=${firsts[firsts.size / 2]}us last=${lasts[lasts.size / 2]}us")
        println("DR-02a NFR budget is 100ms for time-to-FIRST (non-functional.md)")
        println(
            "DR-02a => gap first..exhaustive: cold=${cold?.gapUs}us " +
                "warm=${lasts[lasts.size / 2] - firsts[firsts.size / 2]}us",
        )
    }
}
