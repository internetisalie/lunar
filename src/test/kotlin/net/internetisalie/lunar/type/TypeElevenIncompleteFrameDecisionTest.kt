package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * §3.3 steps 4–7 and §3.7, asserted **as decisions and as mechanisms** — because measurement showed
 * their outcome fixtures no longer attribute.
 *
 * `TypeElevenDr11LateDeclarationTest`, `TypeElevenDr12WarmInnerSnapshotTest`,
 * `TypeElevenDr14InProgressTest` and `TypeElevenDr15LateLibraryAnswerTest` were each measured red
 * against the de-risking scaffold with its own guard removed, one guard at a time, and that is what
 * made them gates. Re-earned against the **shipped** build, one mutation per run:
 *
 * | mutation | DR-11 | DR-12 | DR-14 | DR-15 |
 * | :-- | :-- | :-- | :-- | :-- |
 * | step 4 (`absences`) deleted | **red** | — | — | — |
 * | §3.7 warm reporting deleted | — | *green* | — | — |
 * | step 6 (`inProgressHits`) deleted | — | — | *green* | — |
 * | step 7 (`rescuedGlobals`) deleted | — | — | *green* | **red** |
 *
 * The three greens have one cause: **step 7 subsumes them on any library→library fixture.** A
 * library global that only another library declares resolves through `doResolveGlobal`'s all-scope
 * fallback, so every cross-library reference is a rescued global — and DR-12's and DR-14's fixtures
 * are built entirely out of cross-library references. Two sufficient rejectors for one outcome
 * attribute redness to neither, which is the shape Phase 1's review rejected in `de60eb83`.
 *
 * No fixture can separate them: an in-progress hit needs a library→library cycle (⇒ rescued), and
 * routing the cycle through a project file instead puts an unprovisioned URL in `urls` (⇒ step 3).
 * So the guards are asserted where they are separable — the decision, on a frame with exactly one
 * non-empty set, and the report, on the frame a real fixture produces.
 */
class TypeElevenIncompleteFrameDecisionTest : TypeElevenDefinitionLibraryTestCase() {
    private fun provisionedFile(): VirtualFile {
        val libraryRoot = installDefinitionLibrary("luassert", mapOf("wx.lua" to "---@meta\n\nwx = {}\n"))
        return checkNotNull(libraryRoot.findChild("wx.lua"))
    }

    private fun verdictOn(sourceFrame: LuaTypeSourceRecorder.SourceFrame): Boolean {
        val libraryFile = provisionedFile()
        return runReadAction { LuaTypesSnapshot.isPinnable(psiFileOf(libraryFile), sourceFrame) }
    }

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

    /**
     * The control. Without it every case below would also pass on a predicate that answers `false`
     * unconditionally — the "pins nothing" rule `TypeElevenPinnableCostTest` exists to reject.
     */
    fun testAProvisionedFileWithNothingUnknownIsPinnable() {
        assertTrue(verdictOn(LuaTypeSourceRecorder.SourceFrame()))
    }

    /** §3.3 step 4 / §1.8 B1. Mutation: delete the `absences` clause → red. */
    fun testARecordedAbsenceDeniesThePin() {
        val sourceFrame = LuaTypeSourceRecorder.SourceFrame()
        sourceFrame.absences.add("global:writtenLater")

        assertFalse(
            "a resolution that answered nothing is a dependency on a declaration the user has not " +
                "written yet, and a pinned file is never re-judged",
            verdictOn(sourceFrame),
        )
    }

    /** §3.3 step 5 / §1.8 B4. Mutation: delete the `unreplayedWarm` clause → red. */
    fun testAWarmHitThatCouldNotBeReplayedDeniesThePin() {
        val sourceFrame = LuaTypeSourceRecorder.SourceFrame()
        sourceFrame.unreplayedWarm.add("file:///lost/inner.lua")

        assertFalse(
            "a nested snapshot whose frame is gone contributes sources that cannot be known",
            verdictOn(sourceFrame),
        )
    }

    /** §3.3 step 6 / §1.10 V1. Mutation: delete the `inProgressHits` clause → red. */
    fun testAnInProgressHitDeniesThePin() {
        val sourceFrame = LuaTypeSourceRecorder.SourceFrame()
        sourceFrame.inProgressHits.add("file:///cycle/outer.lua")

        assertFalse(
            "a snapshot still being built on this thread is incomplete by construction, and no " +
                "union can complete it",
            verdictOn(sourceFrame),
        )
    }

    /** §3.3 step 7 / §1.10 V2. Mutation: delete the `rescuedGlobals` clause → red. */
    fun testAGlobalRescuedByTheAllScopeFallbackDeniesThePin() {
        val sourceFrame = LuaTypeSourceRecorder.SourceFrame()
        sourceFrame.rescuedGlobals.add("global:onlyALibraryDeclaresThis")

        assertFalse(
            "the call succeeded, but a project declaration written later out-ranks that answer",
            verdictOn(sourceFrame),
        )
    }

    /**
     * §3.7 steps 2–4, as a **mechanism**: a nested `forFile` served warm replays the inner file's
     * recorded frame into the build that asked, so the outer file is judged on the union.
     *
     * Mutation: delete `reportWarmSnapshot` from `forFile` → red (this is the assertion DR-12 can no
     * longer make). ⚠ It cannot fail spuriously in the other direction: `CachedValueBase` re-runs a
     * provider on a hit whenever `IdempotenceChecker` random checks fire, and a re-run reports into
     * every open frame directly, which is at least as complete as replay.
     */
    fun testAWarmNestedSnapshotReplaysItsFrameIntoTheBuildThatAskedForIt() {
        val libraryRoot =
            installDefinitionLibrary("luassert", mapOf("b.lua" to "---@meta\n\nbGlobal = projectSeed\n"))
        val projectDeclaration = myFixture.addFileToProject("p.lua", "projectSeed = { beforeEdit = 1 }\n")
        announceRootsChange()
        val innerFile = checkNotNull(libraryRoot.findChild("b.lua"))
        val projectUrl = checkNotNull(projectDeclaration.virtualFile?.url)

        val coldFrame = frameOf(snapshotOf(innerFile))
        val warmFrame =
            runReadAction {
                LuaTypeSourceRecorder.recording { LuaTypesSnapshot.forFile(psiFileOf(innerFile)) }.second
            }

        assertTrue(
            "the fixture must record the project file cold, or there is nothing to replay (${coldFrame.urls})",
            coldFrame.urls.contains(projectUrl),
        )
        assertTrue(
            "a nested warm hit must carry the inner file's sources outward, or the outer file is " +
                "pinned while transitively depending on a project file (${warmFrame.urls})",
            warmFrame.urls.contains(projectUrl),
        )
    }

    /**
     * §3.1 step 5c, as **reachability**: the `inProgressSnapshot` early return really is taken for a
     * file other than the one being built, and it really does mark the open frame.
     *
     * This is what stops [testAnInProgressHitDeniesThePin] from being a claim about a set nothing
     * ever fills — the (b) half of §1.9 B5's rule, applied to step 6. Mutation: delete the
     * `reportInProgressHit` call from `forFile` → red.
     */
    fun testAMutualLibraryCycleRecordsAnInProgressHit() {
        val libraryRoot =
            installDefinitionLibrary(
                "luassert",
                mapOf(
                    "outer.lua" to "---@meta\n\nOuterSeed = projectSeed\nOuterGlobal = InnerSeed\n",
                    "inner.lua" to "---@meta\n\nInnerSeed = OuterSeed\n",
                ),
            )
        myFixture.addFileToProject("p.lua", "projectSeed = { beforeEdit = 1 }\n")
        announceRootsChange()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        membersOfGlobal("OuterGlobal", consumer)
        val innerFrame = frameOf(snapshotOf(checkNotNull(libraryRoot.findChild("inner.lua"))))

        assertFalse(
            "driving a mutual library cycle must leave an in-progress mark on the frame of the file " +
                "that met it, or step 6 guards a state nothing ever reaches (${innerFrame.inProgressHits})",
            innerFrame.inProgressHits.isEmpty(),
        )
    }
}
