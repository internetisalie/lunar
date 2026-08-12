package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * TYPE-11-01's acceptance gate — TC-1, and the only assertion that states what this feature *does*.
 *
 * A definition-library file is a pure function of provisioned content, so its snapshot has no
 * business being discarded when the user types in a project file. On `main` it is discarded anyway:
 * `forFile` depends on `PsiModificationTracker.MODIFICATION_COUNT`, which every keystroke ticks, so
 * `A !== B` there. This test is therefore **red on `main`** by construction.
 *
 * `TypeElevenDr04LatencyTest` measures what that costs in milliseconds and asserts nothing (§1.5,
 * TC-1b); a latency threshold in CI is a flake generator and no cross-build ratio is quotable. The
 * correctness claim lives here, as instance identity.
 *
 * Stated mutation: revert design §3.3 step 9 to always pick `MODIFICATION_COUNT` → red.
 */
class TypeElevenPinSurvivesUnrelatedEditTest : TypeElevenDefinitionLibraryTestCase() {
    fun testALibrarySnapshotSurvivesAnUnrelatedProjectEdit() {
        val libraryRoot =
            installDefinitionLibrary(
                "luassert",
                mapOf("wx.lua" to "---@meta\n\n---@class wx\nwx = {}\n\n---@type number\nwx.value = nil\n"),
            )
        val projectFile = myFixture.addFileToProject("unrelated.lua", "local before = 1\n")
        val libraryFile = checkNotNull(libraryRoot.findChild("wx.lua"))

        val firstSnapshot = snapshotOf(libraryFile)
        val recordedFrame = frameOf(firstSnapshot)
        assertTrue(
            "premise: the library file must be judged pinnable, or this test measures today's " +
                "behaviour rather than the feature (frame urls=${recordedFrame.urls} " +
                "absences=${recordedFrame.absences} warm=${recordedFrame.unreplayedWarm} " +
                "inProgress=${recordedFrame.inProgressHits} rescued=${recordedFrame.rescuedGlobals})",
            runReadAction { LuaTypesSnapshot.isPinnable(psiFileOf(libraryFile), recordedFrame) },
        )

        rewriteAssertingRootsAreStill(projectFile, "local after = 2\n")

        val secondSnapshot = snapshotOf(libraryFile)
        assertSame(
            "a keystroke in an unrelated project file must not discard a library file's type " +
                "snapshot — that discard is the whole cost this feature removes",
            firstSnapshot,
            secondSnapshot,
        )
    }
}
