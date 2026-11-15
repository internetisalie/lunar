package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.types.LuaPairScan
import net.internetisalie.lunar.lang.psi.types.LuaTypePairScanDifferential
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.PairScanMark
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * MAINT-61 TC 1 to TC 4: the checker's revisit scan tries only the pairs an earlier visit of the
 * same variable has not seen, and still leaves every pair of the visit's product examined.
 *
 * TC 2 to TC 4 read the six fixtures of design §4 in DR-07's order, in one project, because DR-07's
 * counts were measured in that order.
 */
class LuaPairScanTest : BaseDocumentTest() {
    @Test
    fun firstValueIndexSkipsOnlyThePreviousVisitsProduct() {
        val mark = PairScanMark(up = 2, down = 1)
        assertEquals(listOf(0 to 2, 1 to 0, 1 to 1, 1 to 2), enumeratePairs(mark))
        assertEquals(
            listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1, 1 to 2),
            enumeratePairs(null),
        )
    }

    @Test
    fun everyPairOfEveryVisitIsExamined() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            FIXTURES.forEach { (name, text) ->
                LuaTypePairScanDifferential.start()
                val snapshot =
                    try {
                        readSnapshot(name, text)
                    } finally {
                        LuaTypePairScanDifferential.stop()
                    }
                assertEquals(FULL_SCAN_PAIRS.getValue(name), snapshot.scanComparisonCount(), "$name: full-scan pairs")
                assertEquals(0L, LuaTypePairScanDifferential.misses(), "$name: unexamined pairs after a visit")
            }
        }
    }

    @Test
    fun theDifferentialReportsAnUnexaminedPair() {
        LuaTypePairScanDifferential.start()
        try {
            LuaTypePairScanDifferential.observe(examined = false)
        } finally {
            LuaTypePairScanDifferential.stop()
        }
        assertEquals(1L, LuaTypePairScanDifferential.misses(), "the differential cannot report a miss")
    }

    @Test
    fun aRevisitTriesOnlyUnseenPairs() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            assertEquals(METHOD_PAIR_ATTEMPTS, readUpToMethod().pairAttemptCount())
        }
    }

    @Test
    fun theEdgeCounterMatchesTheClosureAndNoMarkOutlivesTheCheck() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            FIXTURES.forEach { (name, text) ->
                val snapshot = readSnapshot(name, text)
                assertEquals(snapshot.closureSize().toLong(), snapshot.edgeAddCount(), "$name: edge adds")
                assertFalse(snapshot.scanMarksRetained(), "$name: scan marks retained")
            }
        }
    }

    private fun enumeratePairs(mark: PairScanMark?): List<Pair<Int, Int>> =
        (0 until 2).flatMap { d -> (LuaPairScan.firstValueIndex(d, mark) until 3).map { v -> d to v } }

    /** Reads the fixtures in order through `method`, the third, and returns its snapshot. */
    private fun readUpToMethod(): LuaTypesSnapshot =
        FIXTURES.take(METHOD_POSITION + 1).map { (name, text) -> readSnapshot(name, text) }.last()

    private fun readSnapshot(
        name: String,
        text: String,
    ): LuaTypesSnapshot {
        myFixture.configureByText("$name.lua", text)
        return LuaTypesSnapshot.forFile(myFixture.file) as LuaTypesSnapshot
    }

    private companion object {
        const val METHOD_POSITION = 2
        const val METHOD_PAIR_ATTEMPTS = 407L

        val FIXTURES =
            listOf(
                "callback" to
                    "---@param cb fun(x: string)\nlocal function f(cb) end\n" +
                    "f(function(x) local y = x print(y) end)\n",
                "callback2" to
                    "---@param cb fun(x: string): number\nlocal function f(cb) return cb('a') end\n" +
                    "local r = f(function(x) return #x end)\nprint(r + 1)\n",
                "method" to
                    "---@class P61\nlocal P = {}\n---@param n number\nfunction P:set(n) self.n = n end\n" +
                    "local p = P\np:set(1)\nlocal q = p\nq:set('s')\n",
                "fwd" to
                    "local function g(a) return a end\nlocal function h(b) return g(b) end\n" +
                    "local z = h(1)\nlocal w = h('s')\nprint(z, w)\n",
                "chain" to
                    "local function id(v) return v end\nlocal t = {}\nt.a = id(t)\nt.b = id(t.a)\n" +
                    "local u = id(t.b)\nprint(u.a, u.b)\n",
                "hof" to
                    "---@param f fun(a: number): number\n---@return fun(a: number): number\n" +
                    "local function wrap(f) return function(a) return f(a) end end\n" +
                    "local k = wrap(function(a) return a * 2 end)\nlocal m = wrap(k)\nprint(m(1))\n",
            )

        /** DR-07's pair attempts without the skip: the pairs the unmodified loop tries. */
        val FULL_SCAN_PAIRS =
            mapOf(
                "callback" to 46L,
                "callback2" to 232L,
                "method" to 796L,
                "fwd" to 520L,
                "chain" to 506L,
                "hof" to 1474L,
            )
    }
}
