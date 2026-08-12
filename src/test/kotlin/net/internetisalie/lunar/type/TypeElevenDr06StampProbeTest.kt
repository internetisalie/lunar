package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * TYPE-11 DR-06 — **the answer to Gap 2.1**: the `modificationStamp` move at dumb-mode exit is real
 * platform behaviour, not a `DumbModeTestUtils` artifact, so §3.3 step 1 is insurance rather than
 * protection of a reachable defect.
 *
 * `PsiFileImpl.getModificationStamp()` is `myModificationStamp + contextStamp`, and
 * `myModificationStamp` has exactly one writer — `PsiFileImpl.clearCaches()`. Its project-wide route
 * is `FileManagerImpl.clearPsiCaches`, reached from `FileManagerEx.possiblyInvalidatePhysicalPsi()`,
 * reached from `FileManagerImpl.processFileTypesChanged`. And `FileManagerImpl`'s **constructor**
 * subscribes that method to both edges of dumb mode (`FileManagerImpl.java:93-103`):
 *
 * ```java
 * myConnection.subscribe(DumbModeListenerBackgroundable.TOPIC, new DumbModeListenerBackgroundable() {
 *   @Override public void enteredDumbMode() { processFileTypesChanged(false); }
 *   @Override public void exitDumbMode()    { processFileTypesChanged(false); }
 * });
 * ```
 *
 * Core platform, unconditional, no test framework anywhere near it. `forFile` depends on `psiFile`
 * in **both** branches of §3.3 step 9, so a snapshot built while dumb is discarded when dumb mode
 * ends whatever the churn tracker says — which is exactly what design §1.6's two mutations measured
 * and could not explain. The guard stays (one boolean, cannot be wrong) and `TypeElevenDumbModeDecisionTest`
 * continues to gate the *decision* per §1.9 B5.
 *
 * These cases lock the platform premise the answer rests on. If a future platform stops healing,
 * they go red and DR-06 must be re-decided rather than silently inherited.
 */
class TypeElevenDr06StampProbeTest : TypeElevenDefinitionLibraryTestCase() {
    private fun readingOf(
        label: String,
        psiFile: PsiFile,
    ): String =
        "TYPE11-DR06 $label stamp=${psiFile.modificationStamp} " +
            "vfs=${psiFile.virtualFile?.modificationStamp} " +
            "roots=${ProjectRootModificationTracker.getInstance(project).modificationCount} " +
            "psiTick=${PsiModificationTracker.getInstance(project).modificationCount} " +
            "target=${LuaProjectSettings.getInstance(project).state.targetModificationTracker.modificationCount} " +
            "id=${System.identityHashCode(psiFile)}"

    private fun installTwoLibraryFiles() =
        installDefinitionLibrary(
            "luassert",
            mapOf(
                "delta.lua" to "---@meta\n\nlibDumb = sharedByLibrary\n",
                "deltaSource.lua" to "---@meta\n\nsharedByLibrary = { fromLibrary = 1 }\n",
            ),
        )

    /**
     * (a) A dumb episode moves the stamp of a `PsiFile` that already existed before it, **without**
     * ticking the roots tracker — so the healing is not a roots change in disguise, which is the
     * other route to `clearPsiCaches` and the one design §1.11 sighted.
     *
     * The bare event pump is the control: pending events alone move nothing.
     */
    fun testADumbEpisodeMovesTheStampWithoutTickingRoots() {
        val libraryRoot = installTwoLibraryFiles()
        val libraryFile = checkNotNull(libraryRoot.findChild("delta.lua"))
        val libraryPsi = psiFileOf(libraryFile)
        val rootsTracker = ProjectRootModificationTracker.getInstance(project)
        val rootsBefore = rootsTracker.modificationCount

        val stampAtStart = libraryPsi.modificationStamp
        println(readingOf("A0 after install", libraryPsi))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        val afterPump = libraryPsi.modificationStamp
        println(readingOf("A1 after a bare event pump, no dumb mode", libraryPsi))
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            println(readingOf("A2 inside dumb mode", libraryPsi))
        }
        println(readingOf("A3 after leaving dumb mode", libraryPsi))

        assertEquals("a bare event pump is not what moves the stamp", stampAtStart, afterPump)
        assertTrue(
            "a dumb episode must move the file's own modificationStamp — that is why a snapshot " +
                "built while dumb cannot survive, and why §3.3 step 1 is insurance rather than a guard",
            libraryPsi.modificationStamp > afterPump,
        )
        assertEquals("the healing must not be a roots tick", rootsBefore, rootsTracker.modificationCount)
    }

    /**
     * (b) **Which** platform event does it. `propFileTypes` is `PsiTreeChangeEvent.PROP_FILE_TYPES`,
     * fired by `FileManagerImpl.processFileTypesChanged` — once on entering dumb mode and once on
     * leaving, from the subscription in `FileManagerImpl`'s constructor.
     */
    fun testBothEdgesOfDumbModeFireAFileTypesPropertyChange() {
        val libraryRoot = installTwoLibraryFiles()
        val libraryFile = checkNotNull(libraryRoot.findChild("delta.lua"))
        val libraryPsi = psiFileOf(libraryFile)
        val observed = mutableListOf<String>()
        PsiManager.getInstance(project).addPsiTreeChangeListener(
            object : PsiTreeChangeAdapter() {
                override fun propertyChanged(event: PsiTreeChangeEvent) {
                    observed.add(event.propertyName.orEmpty())
                    println("TYPE11-DR06 C event=${event.propertyName} stamp=${libraryPsi.modificationStamp}")
                }
            },
            testRootDisposable,
        )

        println(readingOf("C0 before", libraryPsi))
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            println(readingOf("C1 inside", libraryPsi))
        }
        println(readingOf("C2 after", libraryPsi))

        assertEquals(
            "entering and leaving dumb mode must each fire ${PsiTreeChangeEvent.PROP_FILE_TYPES}; " +
                "that event is what invalidates all physical PSI and heals a dumb-built snapshot",
            2,
            observed.count { it == PsiTreeChangeEvent.PROP_FILE_TYPES },
        )
    }

    /**
     * (c) The same statement made about a snapshot that was actually built while dumb: it records
     * `Undefined`, and the file it was built from has moved on by the time dumb mode ends.
     */
    fun testASnapshotBuiltWhileDumbHasAlreadyLostItsFileDependency() {
        val libraryRoot = installTwoLibraryFiles()
        val libraryFile = checkNotNull(libraryRoot.findChild("delta.lua"))
        val libraryPsi = psiFileOf(libraryFile)
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        println(readingOf("B0 before dumb", libraryPsi))
        val stampWhileDumb =
            DumbModeTestUtils.computeInDumbModeSynchronously(project) {
                val dumbType = runReadAction { LuaTypesSnapshot.forFile(libraryPsi).getGlobalType("libDumb") }
                println(readingOf("B1 inside dumb, built", libraryPsi) + " graph=$dumbType")
                libraryPsi.modificationStamp
            }
        println(readingOf("B2 after leaving dumb", libraryPsi))
        val members =
            runReadAction {
                LuaTypeManager
                    .getInstance(project)
                    .resolveGlobal("libDumb", consumer)
                    ?.getMembers()
                    ?.keys
            }
        println(readingOf("B3 after asking again", libraryPsi) + " members=$members")

        assertTrue(
            "the stamp the dumb build depended on must be stale by the time dumb mode ends",
            libraryPsi.modificationStamp > stampWhileDumb,
        )
    }
}
