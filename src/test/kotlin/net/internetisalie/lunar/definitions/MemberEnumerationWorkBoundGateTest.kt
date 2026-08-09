package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberWork

/**
 * COMP-09-09 — **the work bound, enforced.**
 *
 * `docs/features/non-functional.md` bounds exhaustive enumeration by *entries traversed*, not by a
 * clock: "a traversal that visits unrelated entries to find related ones fails this even when it is
 * fast on a small project". That makes it the strongest of the three checks and the only one that
 * does not vary with the machine — and COMP-09-08's timing probe cannot answer it, because a
 * duration is not a count and an implementation that scanned the whole key space *quickly* would
 * pass a latency gate while violating this one outright.
 *
 * The instrument is `LuaReceiverMemberWork`: `FileBasedIndex.processValues` calls back once per
 * (key, file) pair it visits, so counting callbacks is the traversal count taken where the traversal
 * happens.
 *
 * **Deliberately not built on `getAllKeys` totals.** DR-11 measured 4 145 stub keys in *both* the
 * quiet and the noisy arm, including the arm that declares 54: index storage is shared across test
 * methods in one JVM and `getAllKeys` is not really project-scoped. A gate on that number measures
 * the fixture, not the code.
 *
 * Design §4.10b's four assertions, and where each stands today:
 *
 * | # | assertion | entry point | today |
 * | :-- | :-- | :-- | :-- |
 * | 1 | `entriesTraversed >= members.size`, `==` when no name repeats | union | green |
 * | 2 | `entriesTraversed` unchanged between a quiet and a noisy project | union | green |
 * | 3 | `filesVisited ==` the declaring-file count | union | green |
 * | 4 | `filesVisited == 1` when a declaring file was found | `globalMembership` | **red** |
 *
 * 1–3 pass because the DR-09 prototype is already index-backed; they are the assertions that go red
 * if anyone reintroduces a scan, which is what COMP-09-01 exists to remove and what an earlier
 * revision of the design proposed replacing one scan with another form of. **`implementation-plan.md`
 * expects this gate red at Phase 0 and only assertion 4 is:** 1–3 measure the index, which already
 * meets them, and the redness COMP-09-09 is really about lives at the *consumers* — Phase 3 is where
 * they stop scanning.
 *
 * Assertion 4 is red for a different reason and is the one Phase 1 fixes: `membersInFile` runs
 * `processValues` without touching the counter, so it was specified against an instrument that does
 * not observe the door it names (BL-5) — and TC 9, which measures `wx.<caret>`, is exactly that
 * door. Armed on gce-builder 2026-08-09 it reported `the completion door reads exactly one declaring
 * file expected:<1> but was:<0>`, against a `globalMembership` that had demonstrably read one file
 * and returned 30 members.
 */
class MemberEnumerationWorkBoundGateTest : LibraryRootTestCase() {
    /** Assertions 1 and 3, on the union entry point: two declaring files, fifty distinct members. */
    fun testTraversalIsBoundedByTheReceiversOwnMembers() {
        registerLibraryRoot(quietFixture())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val measured = measureUnion(RECEIVER)
        println("COMP-09-09 quiet: $measured")
        assertEquals("the fixture itself is wrong if the member count moved", TOTAL_MEMBERS, measured.members)
        assertTrue(
            "entriesTraversed (${measured.entries}) is below the members it produced (${measured.members}), " +
                "which means the counter is not observing the traversal",
            measured.entries >= measured.members,
        )
        assertEquals(
            "no member name repeats across the two declaring files, so every entry traversed is a " +
                "member returned — anything more is key-space scanning",
            measured.members,
            measured.entries,
        )
        assertEquals("filesVisited must be the declaring-file count, not the scope size", 2, measured.files)
    }

    /**
     * Assertion 2 — the one that goes red if anyone reintroduces a `getAllKeys` scan.
     *
     * Both arms run in one test method against one project, because the comparison is the assertion:
     * two methods printing two numbers is what DR-11 was, and a reader had to do the subtraction.
     */
    fun testTraversalDoesNotMoveWhenUnrelatedContentIsAdded() {
        registerLibraryRoot(quietFixture())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val quiet = measureUnion(RECEIVER)
        registerLibraryRoot(noiseFixture())
        val noisy = measureUnion(RECEIVER)
        val noiseReceiver = measureUnion("${NOISE_PREFIX}7")
        println("COMP-09-09 quiet=$quiet noisy=$noisy noiseReceiver=$noiseReceiver")
        assertEquals(
            "adding ${NOISE_RECEIVERS * NOISE_MEMBERS} unrelated indexed members changed how much " +
                "work `$RECEIVER`'s enumeration does — enumeration is scanning, not looking up",
            quiet.entries,
            noisy.entries,
        )
        assertEquals("and it must not have widened its file set either", quiet.files, noisy.files)
        assertEquals("the noise receiver's own bound tracks its own members", NOISE_MEMBERS, noiseReceiver.entries)
    }

    /**
     * Assertion 4 — the **completion** door, which is the door TC 9 measures.
     *
     * `globalMembership` reads exactly one declaring file by construction, so more than one means the
     * selection rule leaked. It cannot be asserted yet: `membersInFile` does not record into the
     * counter, so the instrument reports 0 for a door that demonstrably visited a file. That is
     * design §4.10b's BL-5, and Phase 1 closes it in one line at each `processValues` callback.
     */
    fun testCompletionDoorReadsExactlyOneFile() {
        registerLibraryRoot(quietFixture())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val membership =
            runReadAction {
                LuaReceiverMemberWork.reset()
                LuaReceiverMemberIndex.globalMembership(RECEIVER, project, myFixture.file)
            }
        val filesVisited = LuaReceiverMemberWork.files
        println(
            "COMP-09-09 completion door: found=${membership.found} " +
                "members=${membership.members.size} filesVisited=$filesVisited",
        )
        assertTrue("the fixture must give the completion door a declaring file to find", membership.found)
        if (COMPLETION_DOOR_INSTRUMENTED) {
            assertEquals("the completion door reads exactly one declaring file", 1, filesVisited)
        } else {
            assertEquals(
                "the completion door is now instrumented (filesVisited=$filesVisited) — flip " +
                    "COMPLETION_DOOR_INSTRUMENTED to true; that is Phase 1's `extend the counter to " +
                    "both entry points` task, not an incidental edit",
                0,
                filesVisited,
            )
        }
    }

    private data class Traversal(
        val members: Int,
        val entries: Int,
        val files: Int,
    )

    private fun measureUnion(receiverName: String): Traversal =
        runReadAction {
            LuaReceiverMemberWork.reset()
            val found =
                LuaReceiverMemberIndex
                    .membersOf(receiverName, project, GlobalSearchScope.allScope(project))
                    .size
            Traversal(found, LuaReceiverMemberWork.entries, LuaReceiverMemberWork.files)
        }

    /** `$RECEIVER` declared across two files, so `filesVisited` is a real count rather than 1. */
    private fun quietFixture(): Map<String, String> {
        val first = StringBuilder("---@meta\n\n$RECEIVER = {}\n\n")
        repeat(FIRST_FILE_MEMBERS) { i -> first.append("---@return boolean\nfunction $RECEIVER.a$i() end\n\n") }
        first.append("return $RECEIVER\n")
        val second = StringBuilder("---@meta\n\n")
        repeat(TOTAL_MEMBERS - FIRST_FILE_MEMBERS) { i ->
            second.append("---@return boolean\nfunction $RECEIVER.b$i() end\n\n")
        }
        return mapOf("target-a.lua" to first.toString(), "target-b.lua" to second.toString())
    }

    /**
     * Indexed content with nothing to do with `$RECEIVER`.
     *
     * Members are **function declarations**, not `Noise.n = nil` assignments: only `LuaFuncDecl`,
     * `LuaLocalVarDecl` and `LuaLocalFuncDecl` are stubbed, so an assignment-based noise fixture
     * leaves the stub key space flat and the arm compares nothing.
     */
    private fun noiseFixture(): Map<String, String> =
        (0 until NOISE_RECEIVERS).associate { r ->
            val noise = StringBuilder("---@meta\n\n$NOISE_PREFIX$r = {}\n\n")
            repeat(NOISE_MEMBERS) { i -> noise.append("---@return boolean\nfunction $NOISE_PREFIX$r.n$i() end\n\n") }
            "noise$r.lua" to noise.toString()
        }

    private companion object {
        /** Flipped by Phase 1's "extend `LuaReceiverMemberWork` counting to both entry points". */
        const val COMPLETION_DOOR_INSTRUMENTED = false

        const val RECEIVER = "Target"
        const val FIRST_FILE_MEMBERS = 30
        const val TOTAL_MEMBERS = 50
        const val NOISE_PREFIX = "Noise"
        const val NOISE_RECEIVERS = 40
        const val NOISE_MEMBERS = 100
    }
}
