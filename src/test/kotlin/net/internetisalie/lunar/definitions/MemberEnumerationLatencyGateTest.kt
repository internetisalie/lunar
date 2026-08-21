package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberWork

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
 * **Assertion 2 no longer exists in that form.** Phase 2 replaced it with a **count** — design
 * §4.10a-bis — because its timing derivation flipped verdict between two runs of the same code once the
 * arm it was measuring got fast enough that the noise floor *was* the measurement. Assertion 1 is
 * unchanged in kind and is now enforcing.
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
    /**
     * Assertion 1: cold time-to-first against the `non-functional.md` budget, tier 1.
     *
     * **Median of five distinct wide receivers, each in its own file** (Phase 2). A single receiver
     * cannot be re-measured cold — the per-file `LuaTypesSnapshot` is memoized, so sample two onwards
     * measure memoization — and the same one receiver produced 13 783 / 35 416 / 49 403 µs across three
     * runs of the same code (design §1.10.2). Five distinct receivers give a median that is a property
     * of the code rather than of which sample the machine happened to schedule well.
     */
    fun testColdTimeToFirstIsWithinTheBudget() {
        installFirstElementProbe()
        registerLibraryRoot(fixture())
        val wideUs = (0 until WIDE_RECEIVERS).map { timeToFirstUs("$WIDE_PREFIX$it.<caret>\n") }.sorted()
        val opaque = timeCompletion("$OPAQUE_RECEIVER.<caret>\n")
        println("COMP-09-08 tier 1 (syntactic, $WIDE_MEMBERS members) cold samples (us) = $wideUs")
        println("COMP-09-08 tier 2 (require-bound) cold time-to-first = $opaque  [RECORDED, not asserted]")
        assertTrue(
            "the probe saw no completion result at all, so nothing was measured (wide=$wideUs)",
            wideUs.size == WIDE_RECEIVERS && wideUs.all { it >= 0 },
        )
        val medianUs = wideUs[WIDE_RECEIVERS / 2]
        assertGate(
            "cold time-to-first for a $WIDE_MEMBERS-member receiver was ${medianUs / 1000} ms — median of " +
                "$WIDE_RECEIVERS distinct receivers, each in its own file — against a $BUDGET_MS ms budget",
            medianUs < BUDGET_MS * 1000,
        )
    }

    /**
     * Assertion 2: the NFR's independence clause — **as a COUNT, not a timing ratio** (design
     * §4.10a-bis, Phase 2).
     *
     * **The timing form was replaced, not tuned, and its own success broke it.** It derived a noise
     * floor as `ceil(p95 / p50)` over five cold *narrow* receivers, which worked while the narrow
     * receivers were served by the graph and their cost was dominated by real work. Served from the
     * index they cost almost nothing but the platform's fixed completion overhead, so `p95/p50` measures
     * scheduler jitter on a ~6 ms constant. Three armed runs of the *same* prototype disagreed — 1x
     * (met), 3x and 4x (not met) — while assertion 1 was comfortably met in all three. That is DR-08's
     * standing rule firing: **gate on a count, never a timing threshold, wherever a count will do.**
     *
     * A count will do, and it already exists. The quantity this assertion is trying to express is "work
     * does not track member count", and `LuaReceiverMemberWork.entries` measures exactly that at the
     * traversal site: `FileBasedIndex.processValues` calls back once per (key, file) pair, so counting
     * callbacks **is** the traversal count. It is machine-independent and cannot flip on jitter.
     *
     * Asked at the **completion door** (`globalMembership`) — the door the hoisted site actually uses —
     * rather than through `completeBasic`, because `LuaReceiverMemberWork` is a `ThreadLocal` and is
     * only readable on the thread that recorded into it.
     *
     * **What the count cannot say** is that a per-entry cost did not explode; assertion 1 covers that.
     * 3 600 entries under a 100 ms wall budget bounds the per-entry cost at 27 µs, against a measured
     * index read of 1 010–3 576 µs for those 3 600 entries (design §1.10.7), two orders inside it.
     */
    fun testTraversalDoesNotTrackMemberCount() {
        registerLibraryRoot(fixture())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val narrowBefore = entriesToAnswer("${NARROW_PREFIX}0")
        val wideBefore = entriesToAnswer("${WIDE_PREFIX}0")
        registerLibraryRoot(unrelatedFixture())
        val narrowAfter = entriesToAnswer("${NARROW_PREFIX}0")
        val wideAfter = entriesToAnswer("${WIDE_PREFIX}0")
        println("COMP-09-08 entries narrow=$narrowBefore->$narrowAfter wide=$wideBefore->$wideAfter")
        assertEquals(
            "the narrow receiver's traversal is its own member count, plus its assignment mark",
            NARROW_MEMBERS + ASSIGNMENT_MARKS,
            narrowBefore,
        )
        assertEquals(
            "the wide receiver's traversal is its own member count, plus its assignment mark",
            WIDE_MEMBERS + ASSIGNMENT_MARKS,
            wideBefore,
        )
        assertEquals(
            "adding ${UNRELATED_RECEIVERS * UNRELATED_MEMBERS} unrelated indexed members moved the " +
                "narrow receiver's work — enumeration is scanning, not looking up",
            narrowBefore,
            narrowAfter,
        )
        assertEquals("and it must not have moved the wide receiver's either", wideBefore, wideAfter)
    }

    /** Entries the **completion door** traverses to answer [receiverName]. */
    private fun entriesToAnswer(receiverName: String): Int =
        runReadAction {
            LuaReceiverMemberWork.reset()
            LuaReceiverMemberIndex.globalMembership(receiverName, project, myFixture.file)
            LuaReceiverMemberWork.entries
        }

    /**
     * The direction switch for assertion 1, **flipped to enforcing by Phase 2**.
     *
     * While [BUDGET_ENFORCED] was false the gate asserted the **miss** — the same measured quantity
     * against the same budget, inverted — so it went red the moment COMP-09-08 was met and Phase 2
     * could not land without flipping it. It kept a known-unfixed requirement from parking a red test on
     * main for three phases, and it is what reported the budget being met (24 ms against 100 ms) on the
     * run that flipped it. Assertion 2 no longer routes through here: a count has no "miss" direction to
     * assert, so it holds unconditionally.
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

    /**
     * One receiver per file: a shared file would make every receiver after the first warm.
     *
     * There are [WIDE_RECEIVERS] wide receivers rather than one because a **cold** measurement is
     * single-use per file, so a median of five needs five files.
     */
    private fun fixture(): Map<String, String> {
        val files = mutableMapOf<String, String>()
        repeat(WIDE_RECEIVERS) { r -> files["wide$r.lua"] = wideFile("$WIDE_PREFIX$r") }
        repeat(NARROW_RECEIVERS) { r -> files["narrow$r.lua"] = narrowFile("$NARROW_PREFIX$r") }
        files["luassert.lua"] = Comp09GoldenFixture.files().getValue("luassert.lua")
        files["opaque.lua"] = "---@meta\n\n$OPAQUE_RECEIVER = require(\"luassert\")\n"
        return files
    }

    private fun wideFile(receiverName: String): String {
        val wide = StringBuilder("---@meta\n\n---@class $receiverName\n$receiverName = {}\n\n")
        repeat(WIDE_FIELDS) { i -> wide.append("---@type number\n$receiverName.wxC_$i = nil\n\n") }
        repeat(WIDE_MEMBERS - WIDE_FIELDS) { i ->
            wide.append("---@return boolean\nfunction $receiverName.f$i() end\n\n")
        }
        wide.append("return $receiverName\n")
        return wide.toString()
    }

    private fun narrowFile(receiverName: String): String {
        val narrow = StringBuilder("---@meta\n\n---@class $receiverName\n$receiverName = {}\n\n")
        repeat(NARROW_MEMBERS) { i -> narrow.append("---@type number\n$receiverName.n$i = nil\n\n") }
        return narrow.toString()
    }

    /** Indexed content with nothing to do with either measured receiver — assertion 2's second arm. */
    private fun unrelatedFixture(): Map<String, String> =
        (0 until UNRELATED_RECEIVERS).associate { r ->
            val noise = StringBuilder("---@meta\n\nUnrelated$r = {}\n\n")
            repeat(UNRELATED_MEMBERS) { i -> noise.append("---@return boolean\nfunction Unrelated$r.n$i() end\n\n") }
            "unrelated$r.lua" to noise.toString()
        }

    private companion object {
        /** Flipped to `true` by Phase 2, whose goal is exactly "COMP-09-08 goes green". */
        const val BUDGET_ENFORCED = true

        /** `docs/features/non-functional.md`: time-to-first-result, tier 1. Not a budget of ours. */
        const val BUDGET_MS = 100L

        const val WIDE_PREFIX = "Wide"
        const val WIDE_RECEIVERS = 5
        const val WIDE_MEMBERS = 3600
        const val WIDE_FIELDS = 3400

        /**
         * BUG-438’s `LuaReceiverMember.ASSIGNED_MEMBER` — one entry per receiver that writes a
         * member through an assignment, which both fixtures here do (`R.n0 = nil`, `R.wxC_0 = nil`).
         *
         * It is **counted, not filtered out**: `LuaReceiverMemberWork` measures the entries the
         * traversal actually reads, and a counter that quietly skipped a stored entry would
         * understate the work — the one quantity this assertion exists to bound. The independence
         * claim is unaffected, because a constant does not track member count: that claim is the two
         * before-versus-after assertions below, and they are what would catch a scan.
         */
        const val ASSIGNMENT_MARKS = 1

        const val NARROW_PREFIX = "Narrow"
        const val NARROW_RECEIVERS = 5
        const val NARROW_MEMBERS = 3
        const val OPAQUE_RECEIVER = "Opaque"
        const val UNRELATED_RECEIVERS = 40
        const val UNRELATED_MEMBERS = 100
    }
}
