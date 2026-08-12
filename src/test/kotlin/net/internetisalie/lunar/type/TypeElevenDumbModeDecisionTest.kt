package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.DumbModeTestUtils
import net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * TYPE-11-05's gate — TC-16, and the answer to §1.6's "this guard has no reproducing test".
 *
 * The *outcome* does not reproduce: with §3.3 step 1 removed, `TypeElevenDr05DumbModeTest` stayed
 * green under two separate mutations, because the library `PsiFile`'s own `modificationStamp` moves
 * when dumb mode ends and `forFile` depends on the file. "The staleness is not reproducible" was
 * then allowed to imply "the guard is not testable", and that does not follow: **the guard is a
 * decision, and the decision is assertable** (§1.9 B5).
 *
 * While dumb, `resolveGlobal` returns null *before* the absence is reported, so a dumb build records
 * an **empty** frame — and on a provisioned file an empty frame clears §3.3 steps 2 through 7. Step
 * 1 is the sole rejector, which is exactly the condition under which deleting it must move an
 * assertion.
 *
 * **Stated mutation: delete §3.3 step 1** → [testTheDecisionIsNoWhileIndexing] goes red.
 */
class TypeElevenDumbModeDecisionTest : TypeElevenDefinitionLibraryTestCase() {
    private fun installTwoLibraryFiles() =
        installDefinitionLibrary(
            "luassert",
            mapOf(
                "delta.lua" to "---@meta\n\nlibDumb = sharedByLibrary\n",
                "epsilon.lua" to "---@meta\n\nlibSmart = sharedByLibrary\n",
                "deltaSource.lua" to "---@meta\n\nsharedByLibrary = { fromLibrary = 1 }\n",
            ),
        )

    /**
     * (a) The decision itself, on an explicitly empty frame — the mutation-detecting assertion.
     *
     * A fresh `SourceFrame` is passed rather than a shared constant because the type holds five
     * mutable sets; there is deliberately no `EMPTY` singleton to be written through.
     */
    fun testTheDecisionIsNoWhileIndexing() {
        val libraryRoot = installTwoLibraryFiles()
        val libraryFile = checkNotNull(libraryRoot.findChild("delta.lua"))

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val verdict =
                runReadAction {
                    LuaTypesSnapshot.isPinnable(psiFileOf(libraryFile), LuaTypeSourceRecorder.SourceFrame())
                }
            assertFalse(
                "a snapshot built while the indexes are unavailable records nothing, which makes it " +
                    "look maximally pinnable exactly when it is least trustworthy",
                verdict,
            )
        }
    }

    /**
     * (b) The state (a) reasons about is the state a real dumb build produces: an empty frame.
     *
     * Without (c) this passes under a **completely inert** recorder — a Phase-1 recorder wired to
     * nothing satisfies "every set empty" — which is why the two cases are asserted together.
     */
    fun testARealDumbBuildRegistersAnEmptyFrame() {
        val libraryRoot = installTwoLibraryFiles()
        val libraryFile = checkNotNull(libraryRoot.findChild("delta.lua"))

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val dumbFrame = LuaTypeSourceRecorder.snapshotFrames[snapshotOf(libraryFile)]

            assertNotNull("the dumb build must register a frame at all, or (a) is about a fiction", dumbFrame)
            val frame = checkNotNull(dumbFrame)
            assertEquals("urls", emptySet<String>(), frame.urls)
            assertEquals("absences", emptySet<String>(), frame.absences)
            assertEquals("unreplayedWarm", emptySet<String>(), frame.unreplayedWarm)
            assertEquals("inProgressHits", emptySet<String>(), frame.inProgressHits)
            assertEquals("rescuedGlobals", emptySet<String>(), frame.rescuedGlobals)
        }
    }

    /**
     * (c) The same shape of file, built only in **smart** mode, records something.
     *
     * ⚠ It must be a **different file** from (b): `snapshotFrames` is keyed on the snapshot instance
     * and nothing invalidates `delta.lua`'s between the two, so a same-file version would read (b)'s
     * dumb empty frame and be red against a correct implementation — its outcome decided by the
     * unresolved dumb-exit `modificationStamp` question (§1.6, Gap 2.1) rather than by the recorder.
     *
     * This is what makes (b) mean "dumb mode records nothing" instead of "nothing is ever recorded".
     */
    fun testTheSameShapeOfFileBuiltSmartRecordsItsSource() {
        val libraryRoot = installTwoLibraryFiles()
        val libraryFile = checkNotNull(libraryRoot.findChild("epsilon.lua"))

        val smartFrame = frameOf(snapshotOf(libraryFile))

        assertFalse(
            "a smart build of the same shape of file must record the file its free global was read " +
                "from, or (b)'s emptiness says nothing about dumb mode",
            smartFrame.urls.isEmpty(),
        )
    }
}
