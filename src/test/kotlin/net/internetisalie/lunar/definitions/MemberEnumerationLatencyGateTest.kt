package net.internetisalie.lunar.definitions

import kotlin.math.ceil

/**
 * COMP-09-08 — **the latency target, enforced instead of documented.**
 *
 * `docs/features/non-functional.md` has always stated "time-to-first-result under 100 ms,
 * independent of index size *and* of how many candidates match". Nothing enforced it: every
 * assertion in the performance suite is `assertTrue(elapsed > 0)` with a comment saying so, and that
 * suite is excluded from the routine loop behind `-PwithPerf`. A 129x miss was therefore invisible
 * until a feature tripped over it (BUG-429). This gate runs in the routine loop, on purpose.
 *
 * **Two assertions, both red on today's code** (design §1.9/§4.10a), so the "must fail first"
 * obligation is met by construction rather than by breaking something on purpose. Run with
 * [BUDGET_ENFORCED] armed, on gce-builder, 2026-08-09, before anything was changed:
 *
 * | # | assertion | armed against today's code |
 * | :-- | :-- | :-- |
 * | 1 | cold time-to-first < the budget | **red** — "was 1054 ms against a 100 ms budget" |
 * | 2 | narrow and wide receivers within the harness's own noise floor | **red** — "scales 64x against a measured noise floor of 2x" |
 *
 * The floor came out at 2x from five cold 3-member receivers at 10 018 / 10 142 / 10 392 / 10 620 /
 * 11 604 us — `ceil(p95/p50)` = `ceil(11604/10392)` = 2. **The next run on the same machine gave a
 * floor of 3x and a ratio of 37x**, which is the whole argument for deriving the factor rather than
 * freezing one: the floor moves, the verdict does not. Design §1.9 predicted 746 ms and 40x on a
 * different machine; the miss is the same in kind under all three.
 *
 * **Tier 1 only, and tier 2 is recorded rather than excluded.** The budget applies to a receiver
 * whose members are syntactically declared — every generated definition library, TARGET-10 included.
 * A receiver bound through `require` resolves through the type graph and cannot meet it by
 * construction (design §4.12). An earlier draft handled that by pointing the gate at a fixture that
 * passes; instead the contract moved, and the slow tier is measured and printed here so the choice
 * stays visible.
 *
 * **Cold is the whole difficulty.** Warm time-to-first is ~1 ms, so a gate that does not force a cold
 * state passes trivially and forever. Every measured receiver therefore lives in its **own** library
 * file, because the per-file `LuaTypesSnapshot` is memoized.
 */
class MemberEnumerationLatencyGateTest : TimedCompletionTestCase() {
    /** Assertion 1: cold time-to-first against the `non-functional.md` budget, tier 1. */
    fun testColdTimeToFirstIsWithinTheBudget() {
        installFirstElementProbe()
        registerLibraryRoot(fixture())
        val wide = timeCompletion("$WIDE_RECEIVER.<caret>\n")
        val opaque = timeCompletion("$OPAQUE_RECEIVER.<caret>\n")
        println("COMP-09-08 tier 1 (syntactic, $WIDE_MEMBERS members) cold time-to-first = $wide")
        println("COMP-09-08 tier 2 (require-bound) cold time-to-first = $opaque  [RECORDED, not asserted]")
        val firstUs = wide?.firstUs ?: -1
        assertTrue("the probe saw no completion result at all, so nothing was measured", firstUs >= 0)
        assertGate(
            "cold time-to-first for `$WIDE_RECEIVER.` was ${firstUs / 1000} ms against a $BUDGET_MS ms budget",
            firstUs < BUDGET_MS * 1000,
        )
    }

    /**
     * Assertion 2: the NFR's independence clause. Time-to-first must not track member count.
     *
     * The factor is **derived, not picked** (DR-17): `ceil(p95 / p50)` over five cold samples of the
     * *narrow* receiver alone — the harness's own noise floor, measured on a case where member count
     * cannot be the variable. A wide-vs-narrow ratio inside that band is independence; outside it is
     * not. Each narrow sample is a different receiver in a different file, because a second sample of
     * the same receiver is warm and would measure memoization.
     */
    fun testTimeToFirstIsIndependentOfMemberCount() {
        installFirstElementProbe()
        registerLibraryRoot(fixture())
        val narrowUs = (0 until NARROW_RECEIVERS).map { timeToFirstUs("$NARROW_PREFIX$it.<caret>\n") }.sorted()
        val wideUs = timeToFirstUs("$WIDE_RECEIVER.<caret>\n")
        val floor = narrowUs[narrowUs.size / 2]
        assertTrue("no narrow sample was observed, so there is no noise floor to compare against", floor > 0)
        val factor = ceil(narrowUs.last().toDouble() / floor).toInt()
        val ratio = (wideUs / floor).toInt()
        println("COMP-09-08 narrow cold samples (us) = $narrowUs  p50=$floor p95=${narrowUs.last()}")
        println("COMP-09-08 noise floor factor = ${factor}x; wide ($WIDE_MEMBERS members) = ${wideUs}us = ${ratio}x")
        assertGate(
            "time-to-first scales ${ratio}x with member count against a measured noise floor of ${factor}x",
            ratio <= factor,
        )
    }

    /**
     * One direction switch for both assertions, so neither can be quietly left behind.
     *
     * While [BUDGET_ENFORCED] is false the gate asserts the **miss** — the same measured quantity
     * against the same budget, inverted — so it goes red the moment COMP-09-08 is met and Phase 2
     * cannot land without flipping it. That is Phase 2's exit criterion, and it keeps a
     * known-unfixed requirement from parking a red test on main for three phases.
     */
    private fun assertGate(
        finding: String,
        met: Boolean,
    ) {
        if (BUDGET_ENFORCED) {
            assertTrue("COMP-09-08: $finding", met)
        } else {
            assertFalse(
                "COMP-09-08 is now MET — $finding. Flip BUDGET_ENFORCED to true; that is Phase 2's " +
                    "exit criterion, not an incidental edit",
                met,
            )
        }
    }

    /** One receiver per file: a shared file would make every receiver after the first warm. */
    private fun fixture(): Map<String, String> {
        val wide = StringBuilder("---@meta\n\n---@class $WIDE_RECEIVER\n$WIDE_RECEIVER = {}\n\n")
        repeat(3400) { i -> wide.append("---@type number\n$WIDE_RECEIVER.wxC_$i = nil\n\n") }
        repeat(200) { i -> wide.append("---@return boolean\nfunction $WIDE_RECEIVER.f$i() end\n\n") }
        wide.append("return $WIDE_RECEIVER\n")
        val files = mutableMapOf("wide.lua" to wide.toString())
        repeat(NARROW_RECEIVERS) { r ->
            val narrow = StringBuilder("---@meta\n\n---@class $NARROW_PREFIX$r\n$NARROW_PREFIX$r = {}\n\n")
            repeat(3) { i -> narrow.append("---@type number\n$NARROW_PREFIX$r.n$i = nil\n\n") }
            files["narrow$r.lua"] = narrow.toString()
        }
        files["luassert.lua"] = Comp09GoldenFixture.files().getValue("luassert.lua")
        files["opaque.lua"] = "---@meta\n\n$OPAQUE_RECEIVER = require(\"luassert\")\n"
        return files
    }

    private companion object {
        /** Flipped by Phase 2, whose goal is exactly "COMP-09-08 goes green". */
        const val BUDGET_ENFORCED = false

        /** `docs/features/non-functional.md`: time-to-first-result, tier 1. Not a budget of ours. */
        const val BUDGET_MS = 100L

        const val WIDE_RECEIVER = "wx"
        const val WIDE_MEMBERS = 3600
        const val NARROW_PREFIX = "Narrow"
        const val NARROW_RECEIVERS = 5
        const val OPAQUE_RECEIVER = "Opaque"
    }
}
