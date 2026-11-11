package net.internetisalie.lunar.corpus

import net.internetisalie.lunar.corpus.SnapshotCostVerdict.EDIT_CASCADE_ITEM
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.OWN_BUILD_ITEM
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.REACH_ITEM
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.RUN_LIMIT_MS
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.attribute
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.coverage
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.parseExceptions
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.parseRoadmapIds
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.rankedLines
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.releaseLines
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.runTimeFailures
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.staleExceptions
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.violations
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * MAINT-60 design §8: the snapshot cost sweep's verdict, from rows alone (TC 1, 2, 3, 6, 7). Routine and
 * fixture-free. Each case names the mutation of [SnapshotCostVerdict] that turns it red.
 */
class SnapshotCostVerdictTest {
    private val ids = setOf(OWN_BUILD_ITEM, REACH_ITEM, EDIT_CASCADE_ITEM)
    private val exceptionsHeader = "member\tfile\tbound\titem\tms"

    /** A member `m` row with no build records, so a missing pass is reach (cold) or edit cascade (edit). */
    private fun row(
        cold: Long,
        edit: Long,
    ): SweepRow = SweepRow("m", "f.lua", 1, false, cold, "ok", -1, "", -1, 0, edit, "ok", -1, "", -1, 0, 0)

    private fun ex(
        bound: String,
        file: String = "f.lua",
    ): CostException = CostException("m", file, bound, if (bound == "cold-250") REACH_ITEM else EDIT_CASCADE_ITEM)

    private fun tens(count: Int): List<SweepRow> = (0 until count).map { row(10, 10).copy(file = "f$it.lua") }

    private fun falloRow(): SweepRow =
        row(60_047, 0).copy(
            member = "fallo",
            file = "lua/fallo/init.lua",
            coldStatus = "timeout",
            rootSelfMs = 59_982,
            editStatus = "not-run",
        )

    private fun compilerRow(): SweepRow =
        row(60_002, 0).copy(
            member = "luals",
            file = "script/vm/compiler.lua",
            coldStatus = "timeout",
            rootSelfMs = 205,
            topDep = "/x/test/corpus/luals/script/parser/compile.lua",
            topDepSelfMs = 45_000,
            editStatus = "not-run",
        )

    private fun selectedRow(
        member: String,
        file: String,
    ): SweepRow = row(10, 10).copy(member = member, file = file)

    @Test
    fun testV1UnlistedColdMiss() {
        assertEquals(
            listOf("m/f.lua cold-250 300 ms: no exception"),
            violations(listOf(row(300, 10)), emptyList(), ids),
        )
    }

    @Test
    fun testV2ListedColdMiss() {
        assertEquals(emptyList(), violations(listOf(row(300, 10)), listOf(ex("cold-250")), ids))
    }

    @Test
    fun testV3NoRoadmapRow() {
        val listing = CostException("m", "f.lua", "cold-250", "MAINT-99")
        assertEquals(
            listOf("MAINT-99: no roadmap row", "m/f.lua cold-250: misfiled under MAINT-99, attributed to $REACH_ITEM"),
            violations(listOf(row(300, 10)), listOf(listing), ids),
        )
    }

    @Test
    fun testV4KeyIncludesBound() {
        val rows = listOf(row(300, 10))
        val exceptions = listOf(ex("edit-250"))
        assertEquals(listOf("m/f.lua cold-250 300 ms: no exception"), violations(rows, exceptions, ids))
        assertEquals(listOf("m/f.lua edit-250 10 ms: stale exception: remove"), staleExceptions(rows, exceptions))
    }

    @Test
    fun testV5StaleListing() {
        val rows = listOf(row(150, 10))
        val exceptions = listOf(ex("cold-250"))
        assertEquals(emptyList(), violations(rows, exceptions, ids))
        assertEquals(listOf("m/f.lua cold-250 150 ms: stale exception: remove"), staleExceptions(rows, exceptions))
    }

    @Test
    fun testV6ListingInBandIsNotStale() {
        val rows = listOf(row(220, 10))
        val exceptions = listOf(ex("cold-250"))
        assertEquals(emptyList(), violations(rows, exceptions, ids))
        assertEquals(emptyList(), staleExceptions(rows, exceptions))
    }

    @Test
    fun testV7UnlistedInBandIsNoViolation() {
        assertEquals(emptyList(), violations(listOf(row(240, 10)), emptyList(), ids))
    }

    @Test
    fun testV8UnlistedEditMiss() {
        assertEquals(
            listOf("m/f.lua edit-250 300 ms: no exception"),
            violations(listOf(row(10, 300)), emptyList(), ids),
        )
    }

    @Test
    fun testV9NotRunMisses() {
        assertEquals(
            listOf("m/f.lua edit-250 not-run: no exception"),
            violations(listOf(row(10, 0).copy(editStatus = "not-run")), emptyList(), ids),
        )
    }

    @Test
    fun testV10SkippedMisses() {
        assertEquals(
            listOf("m/f.lua cold-250 skipped: no exception", "m/f.lua edit-250 skipped: no exception"),
            violations(listOf(row(0, 0).copy(coldStatus = "skipped", editStatus = "skipped")), emptyList(), ids),
        )
    }

    @Test
    fun testV11SweepErrorIsNeverExcepted() {
        val failed = row(0, 0).copy(coldStatus = "exception:IllegalStateException", editStatus = "not-run")
        assertEquals(
            listOf("m/f.lua cold-250 exception:IllegalStateException: sweep error"),
            violations(listOf(failed), listOf(ex("cold-250"), ex("edit-250")), ids),
        )
    }

    @Test
    fun testV12ListingForAnUnselectedFile() {
        assertEquals(
            listOf("m/g.lua: exception for an unselected file"),
            violations(listOf(row(10, 10)), listOf(ex("cold-250", "g.lua")), ids),
        )
    }

    @Test
    fun testV13Misfiled() {
        val listing = CostException("m", "f.lua", "cold-250", OWN_BUILD_ITEM)
        assertEquals(
            listOf("m/f.lua cold-250: misfiled under $OWN_BUILD_ITEM, attributed to $REACH_ITEM"),
            violations(listOf(row(300, 10)), listOf(listing), ids),
        )
    }

    @Test
    fun testP1NearestRankIndex() {
        assertTrue("p99 cold 10 ms: PASS" in releaseLines(tens(99) + row(500, 10), emptyList(), 0))
    }

    @Test
    fun testP2P99CountsExceptedRows() {
        val rows = tens(98) + row(500, 10).copy(file = "x.lua") + row(500, 10).copy(file = "y.lua")
        val exceptions = listOf(ex("cold-250", "x.lua"), ex("cold-250", "y.lua"))
        assertTrue("p99 cold 500 ms: FAIL" in releaseLines(rows, exceptions, 0))
    }

    @Test
    fun testP3P99CountsUnmeasuredAsOver() {
        val rows =
            tens(98) + row(0, 10).copy(coldStatus = "timeout", file = "x.lua") +
                row(0, 10).copy(coldStatus = "timeout", file = "y.lua")
        assertTrue("p99 cold unmeasured: FAIL" in releaseLines(rows, emptyList(), 0))
    }

    @Test
    fun testP4EditP99ReadsEditTimes() {
        val rows = tens(98) + row(10, 500).copy(file = "x.lua") + row(10, 500).copy(file = "y.lua")
        val lines = releaseLines(rows, emptyList(), 0)
        assertTrue("p99 cold 10 ms: PASS" in lines)
        assertTrue("p99 edit 500 ms: FAIL" in lines)
    }

    @Test
    fun testP5ReleaseReady() {
        assertEquals("release READY", releaseLines(tens(100), emptyList(), 0).last())
    }

    @Test
    fun testP6ListingThatNoLongerMissesDoesNotHold() {
        val lines = releaseLines(tens(100), listOf(ex("cold-250", "f1.lua")), 0)
        assertTrue("remove before tagging: m/f1.lua cold-250" in lines)
        assertEquals("release READY", lines.last())
    }

    @Test
    fun testP7P99HoldsRelease() {
        val rows = tens(98) + row(150, 10).copy(file = "x.lua") + row(150, 10).copy(file = "y.lua")
        val lines = releaseLines(rows, emptyList(), 0)
        assertTrue("p99 cold 150 ms: FAIL" in lines)
        assertTrue("misses 0: PASS" in lines)
        assertEquals("release HOLD", lines.last())
    }

    @Test
    fun testP8ChecksHoldRelease() {
        val lines = releaseLines(tens(100), emptyList(), 1)
        assertTrue("checks 1: FAIL" in lines)
        assertEquals("release HOLD", lines.last())
    }

    @Test
    fun testP9ListedMissHoldsRelease() {
        val lines = releaseLines(tens(99) + row(300, 10).copy(file = "x.lua"), listOf(ex("cold-250", "x.lua")), 0)
        assertTrue("p99 cold 10 ms: PASS" in lines)
        assertTrue("misses 1: FAIL" in lines)
        assertEquals("release HOLD", lines.last())
    }

    @Test
    fun testP10NoRows() {
        val lines = releaseLines(emptyList(), emptyList(), 0)
        assertTrue("p99 cold unmeasured: FAIL" in lines)
        assertTrue("p99 edit unmeasured: FAIL" in lines)
        assertEquals("release HOLD", lines.last())
    }

    @Test
    fun testT1RunLimitReached() {
        assertEquals(listOf("sweep took 45 min"), runTimeFailures(RUN_LIMIT_MS))
    }

    @Test
    fun testT2UnderRunLimit() {
        assertEquals(emptyList(), runTimeFailures(RUN_LIMIT_MS - 1))
    }

    @Test
    fun testC1SelectedFileWithNoRow() {
        assertEquals(
            listOf("m/b.lua: no row"),
            coverage(mapOf("m" to listOf("a.lua", "b.lua")), listOf(selectedRow("m", "a.lua"))),
        )
    }

    @Test
    fun testC2RowForAnUnselectedFile() {
        assertEquals(
            listOf("m/z.lua: row for an unselected file"),
            coverage(mapOf("m" to listOf("a.lua")), listOf(selectedRow("m", "a.lua"), selectedRow("m", "z.lua"))),
        )
    }

    @Test
    fun testC3MemberWithNoSelectedFiles() {
        assertEquals(
            listOf("n: no selected files"),
            coverage(mapOf("m" to listOf("a.lua"), "n" to emptyList()), listOf(selectedRow("m", "a.lua"))),
        )
    }

    @Test
    fun testC4DuplicateRow() {
        assertEquals(
            listOf("m/a.lua: duplicate row"),
            coverage(mapOf("m" to listOf("a.lua")), listOf(selectedRow("m", "a.lua"), selectedRow("m", "a.lua"))),
        )
    }

    @Test
    fun testA1OwnBuildOfANamedFile() {
        val fallo = falloRow()
        assertEquals("own build" to "BUG-96", attribute(fallo, "cold-250", listOf(fallo)).causeAndItem())
    }

    @Test
    fun testA2ReachFiledUnderTheDominantDependency() {
        val compiler = compilerRow()
        assertEquals("reach" to "BUG-97", attribute(compiler, "cold-250", listOf(compiler)).causeAndItem())
    }

    @Test
    fun testA3NotRunFiledUnderItsColdPass() {
        val fallo = falloRow()
        assertEquals("unmeasured" to "BUG-96", attribute(fallo, "edit-250", listOf(fallo)).causeAndItem())
    }

    @Test
    fun testA4UnmeasuredFiledUnderItsMembersStopper() {
        val stopped =
            row(0, 0).copy(
                member = "luals",
                file = "script/vm/a.lua",
                coldStatus = "unmeasured",
                editStatus = "unmeasured",
            )
        val rows = listOf(falloRow(), compilerRow(), stopped)
        assertEquals("unmeasured" to "BUG-97", attribute(stopped, "cold-250", rows).causeAndItem())
    }

    @Test
    fun testA5EditCascadeReadsTheEditPass() {
        val edited = row(10, 900).copy(topDepSelfMs = 400, editRootSelfMs = 20, editTopDepSelfMs = 120)
        assertEquals("edit cascade" to EDIT_CASCADE_ITEM, attribute(edited, "edit-250", listOf(edited)).causeAndItem())
    }

    @Test
    fun testA6SkippedFiledUnderTheSlowestRead() {
        val skipped = row(0, 0).copy(file = "z.lua", coldStatus = "skipped", editStatus = "skipped")
        val rows = listOf(row(900, 10).copy(file = "x.lua"), falloRow(), skipped)
        assertEquals("unmeasured" to "BUG-96", attribute(skipped, "cold-250", rows).causeAndItem())
    }

    @Test
    fun testA7SweepError() {
        val failed = row(0, 0).copy(coldStatus = "exception:IllegalStateException")
        assertEquals("sweep error" to "", attribute(failed, "cold-250", listOf(failed)).causeAndItem())
    }

    @Test
    fun testE1ParsesAListing() {
        assertEquals(
            listOf(CostException("m", "f.lua", "cold-250", "MAINT-61")),
            parseExceptions("$exceptionsHeader\nm\tf.lua\tcold-250\tMAINT-61\t300"),
        )
    }

    @Test
    fun testE2RefusesFourFields() {
        val text = "$exceptionsHeader\nm\tf.lua\tcold-250\tMAINT-61"
        val failure = assertFailsWith<IllegalArgumentException> { parseExceptions(text) }
        assertTrue("line 2" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun testE3RefusesADuplicateKey() {
        val listing = "m\tf.lua\tcold-250\tMAINT-61\t300"
        val text = "$exceptionsHeader\n$listing\n$listing"
        val failure = assertFailsWith<IllegalArgumentException> { parseExceptions(text) }
        assertTrue("line 3" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun testE4RefusesAnUnknownBound() {
        val text = "$exceptionsHeader\nm\tf.lua\tcold-300\tMAINT-61\t300"
        val failure = assertFailsWith<IllegalArgumentException> { parseExceptions(text) }
        assertTrue("line 2" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun testR1ReadsWaveTablesOnly() {
        val roadmap =
            "| Branch | Tip |\n| BUG-1 | x |\n\n| ID | Title | Status |\n| :--- | :--- | :--- |\n" +
                "| MAINT-61 | t | todo |\n\ntext\n| BUG-2 | y |"
        assertEquals(setOf("MAINT-61"), parseRoadmapIds(roadmap))
    }

    @Test
    fun testK1RanksMissesAndTheBandOnly() {
        assertEquals(
            listOf(SnapshotCostVerdict.RANKED_HEADER, "m\tf.lua\tcold-250\t220 ms\tin band\t\t\t-1\tno"),
            rankedLines(listOf(row(220, 10), row(150, 10).copy(file = "g.lua")), emptyList()),
        )
    }

    private fun Attribution.causeAndItem(): Pair<String, String> = cause to item
}
