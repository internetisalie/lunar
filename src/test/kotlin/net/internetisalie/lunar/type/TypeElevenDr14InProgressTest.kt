package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * TYPE-11 DR-14 — Step 9 blocker V1: is the `LuaTypesVisitor.inProgressSnapshot` early return
 * (`LuaTypes.kt:214`) ever served for a file **other** than the one whose own re-entrant call is
 * directly on the stack, and if so, does it ship a stale type?
 *
 * `design.md` §3.7's last bullet: "it is the same file's own in-flight build, whose frame is the
 * very frame currently open." The guard is a map keyed on the *requested* file
 * (`LuaTypesVisitor.kt:1483-1487`) and `buildSnapshot` adds an entry for every file whose build is
 * on the current thread's call stack (`:1507-1518`) — nothing limits that to one entry.
 *
 * Fixture: two **library** files forming a genuine mutual-reference cycle, with the depended-upon
 * value seeded from a **project** file:
 *
 * ```
 * outer.lua:  OuterSeed = projectSeed        -- statement 1, fully resolves before statement 2
 *             OuterGlobal = InnerSeed        -- statement 2, nests into inner.lua
 * inner.lua:  InnerSeed = OuterSeed          -- resolves back into outer.lua, mid-build
 * ```
 *
 * Resolving `OuterGlobal` starts `forFile(outer.lua)`; while outer.lua's traversal is blocked on
 * `InnerSeed`, `inner.lua`'s traversal (nested one level in) resolves `OuterSeed` and calls
 * `forFile(outer.lua)` again — for the file that is genuinely still on the stack, but **not** the
 * file inner.lua's own build is for. That is "several files legitimately in progress on one
 * thread," not the trivial single-file self-loop the design's wording suggests.
 */
class TypeElevenDr14InProgressTest : TypeElevenDefinitionLibraryTestCase() {
    private fun membersOfGlobal(
        name: String,
        context: PsiFile,
    ): Set<String> =
        runReadAction {
            LuaTypeManager
                .getInstance(project)
                .resolveGlobal(name, context)
                ?.getMembers()
                ?.keys
                ?.toSet()
                .orEmpty()
        }

    private fun captureStdout(body: () -> Unit): String {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer))
        try {
            body()
        } finally {
            System.setOut(original)
        }
        return buffer.toString()
    }

    private fun cycleFiles(): Map<String, String> =
        mapOf(
            "outer.lua" to "---@meta\n\nOuterSeed = projectSeed\nOuterGlobal = InnerSeed\n",
            "inner.lua" to "---@meta\n\nInnerSeed = OuterSeed\n",
        )

    private fun installCycle(): PsiFile {
        installDefinitionLibrary("luassert", cycleFiles())
        return myFixture.addFileToProject("p.lua", "projectSeed = { beforeEdit = 1 }\n")
    }

    /**
     * Q14a — is the interleaving reachable? A trace println at `LuaTypes.kt:214` is the cheapest
     * instrument (per the de-risking round's scaffold); this test only needs the *shape* of the
     * cycle to exist and to be exercised, and is otherwise a plain correctness check so it stays
     * green whether or not the scaffold's trace line is present.
     */
    fun testMutualReferenceCycleBetweenTwoLibraryFilesResolvesWithoutRecursing() {
        installCycle()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        val output =
            captureStdout {
                val outer = membersOfGlobal("OuterGlobal", consumer)
                println("DR-14 OuterGlobal = $outer")
            }
        print(output)

        val innerAlone = membersOfGlobal("InnerSeed", consumer)
        assertEquals(
            "InnerSeed must carry the project seed's members through the mutual-reference cycle",
            setOf("beforeEdit"),
            innerAlone,
        )
    }

    /**
     * Q14b — if reachable, does it ship a stale type? `inner.lua`'s own build is a **normal**,
     * top-level `forFile(inner.lua)` call (nested inside outer.lua's build, not itself a re-entrant
     * hit) — so it is recorded and cached exactly like any other library file. What it records is
     * the defect: `typeOfGlobalIn` reports `outer.lua` as visited (a provisioned file), but the
     * project dependency `outer.lua` itself carries (`p.lua`, recorded only into *outer's* frame,
     * pushed onto the stack before inner's frame existed) never reaches inner's frame, because the
     * re-entrant `forFile(outer.lua)` call inner.lua makes returns via `inProgressSnapshot` and
     * skips the whole recording/replay mechanism (§3.7's own rule, applied to itself).
     *
     * Asserts today's correct answer; green on `main`. The measurement is whether it goes red under
     * the §3 conditional rule as written (no DR-14 guard) and green under the candidate rule.
     */
    fun testALibraryTransitivelyEmbeddingAProjectTypeThroughAReentrantCycleStillTracksIt() {
        val projectFile = installCycle()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        // Drive the cycle: outer.lua's build nests into inner.lua's, which reenters outer.lua.
        val outerBefore = membersOfGlobal("OuterGlobal", consumer)
        println("DR-14 before edit: OuterGlobal = $outerBefore")
        val innerBefore = membersOfGlobal("InnerSeed", consumer)
        println("DR-14 before edit: InnerSeed = $innerBefore")
        assertEquals(
            "InnerSeed must carry the project seed's members before the edit",
            setOf("beforeEdit"),
            innerBefore,
        )

        rewriteAssertingRootsAreStill(projectFile, "projectSeed = { afterEdit = 1 }\n")

        val innerAfter = membersOfGlobal("InnerSeed", consumer)
        println("DR-14 after edit: InnerSeed = $innerAfter")
        assertEquals(
            "editing the project file must be reflected in InnerSeed's type, even though InnerSeed " +
                "was built while outer.lua's build was still in progress on the same thread",
            setOf("afterEdit"),
            innerAfter,
        )
    }

    /**
     * Q14c — **which** §3.3 guard keeps this fixture green when step 6 is deleted? The ledger
     * credits step 7 (`rescuedGlobals`); Phase 3's review (F4) proposed step 4 (`absences`) as a
     * second sufficient rejector, on the reading that re-entering outer.lua's in-flight build for
     * `OuterSeed` answers nothing.
     *
     * Measured, and the review's reading is **refuted**: the re-entrant resolution answers, through
     * `doResolveGlobal`'s all-scope fallback, so the mark it leaves is a *rescued global*, not an
     * absence.
     *
     * ```
     * urls=[…/outer.lua] absences=[] inProgressHits=[…/outer.lua]
     * rescuedGlobals=[global:OuterSeed] unreplayedWarm=[]
     * ```
     *
     * Two sufficient rejectors for one outcome attribute redness to neither — the shape Phase 1's
     * review rejected in `de60eb83` — so this asserts the composition directly instead of inferring
     * it from a mutation's colour. The empty `absences` is the load-bearing half: it is what makes
     * step 7 the sole cause of the step-6 green, exactly as the ledger says.
     */
    fun testTheInProgressFixtureIsRejectedByTheRescuedGlobalAndNotByAnAbsence() {
        val libraryRoot = installDefinitionLibrary("luassert", cycleFiles())
        myFixture.addFileToProject("p.lua", "projectSeed = { beforeEdit = 1 }\n")
        announceRootsChange()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        membersOfGlobal("OuterGlobal", consumer)
        val innerFrame = frameOf(snapshotOf(checkNotNull(libraryRoot.findChild("inner.lua"))))
        println(
            "DR-14 inner.lua frame: urls=${innerFrame.urls} absences=${innerFrame.absences} " +
                "inProgressHits=${innerFrame.inProgressHits} rescuedGlobals=${innerFrame.rescuedGlobals} " +
                "unreplayedWarm=${innerFrame.unreplayedWarm}",
        )

        assertEquals(
            "the re-entrant resolution answered, so nothing here is an absence — step 4 is not a " +
                "second rejector on this fixture",
            emptySet<String>(),
            innerFrame.absences,
        )
        assertTrue(
            "the answer came from the all-scope fallback, which is what step 7 rejects on " +
                "(rescuedGlobals=${innerFrame.rescuedGlobals})",
            innerFrame.rescuedGlobals.contains("global:OuterSeed"),
        )
        assertFalse(
            "and step 6's own mark is present, so the two guards really do overlap here " +
                "(inProgressHits=${innerFrame.inProgressHits})",
            innerFrame.inProgressHits.isEmpty(),
        )
    }
}
