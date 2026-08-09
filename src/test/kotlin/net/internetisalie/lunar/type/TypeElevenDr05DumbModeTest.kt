package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.DumbModeTestUtils
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * TYPE-11 DR-05 — does a snapshot built while `DumbService.isDumb` bake in `resolveGlobal`'s nulls,
 * and do those nulls survive into smart mode?
 *
 * `resolveGlobal` returns null unconditionally while dumb (`LuaTypeManagerImpl:141`), so a library
 * file whose own global is typed from a free global infers `Undefined` for it. TYPE-11-05 asserts
 * that such a snapshot must not outlive dumb mode.
 *
 * Answer, measured: the nulls **are** baked in and they do **not** survive. See the per-test KDoc for
 * why that verdict does not make this a gate.
 */
class TypeElevenDr05DumbModeTest : TypeElevenDefinitionLibraryTestCase() {
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
     * The measurement. Build the library snapshot while dumb, leave dumb mode, then ask for the type
     * without touching any file. Green means the dumb answer did not survive; red means it did.
     *
     * The fixture is deliberately **library→library**: `delta.lua`'s free global is declared in
     * `deltaSource.lua`, which is in the same provisioned tree. That matters — with the free global
     * declared in a *project* file the snapshot records an unprovisioned source and is excluded from
     * pinning for that reason instead, and this test would pass without ever exercising the dumb
     * guard.
     *
     * **This test is NOT a gate, and that is a measured result rather than an admission.** Two
     * mutations were run against the de-risking build and BOTH left it green: removing the `!isDumb`
     * term from `forFile`'s pinnable condition, and replacing the generation tracker with
     * `ModificationTracker.NEVER_CHANGED`. The reason is printed below — the library `PsiFile`'s own
     * `modificationStamp` moves 0 -> 1 when the fixture leaves dumb mode, and `forFile` passes
     * `psiFile` as a dependency, so the snapshot rebuilds whatever the churn tracker says. Whether
     * that stamp move is real platform behaviour or a `DumbModeTestUtils` artifact is TYPE-11-DR-06.
     */
    fun testASnapshotBuiltWhileDumbDoesNotSurviveIntoSmartMode() {
        val root =
            installDefinitionLibrary(
                "luassert",
                mapOf(
                    "delta.lua" to "---@meta\n\nlibDumb = sharedByLibrary\n",
                    "deltaSource.lua" to "---@meta\n\nsharedByLibrary = { fromLibrary = 1 }\n",
                ),
            )
        announceRootsChange()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")
        val libraryFile = checkNotNull(root.findChild("delta.lua"))

        var psiDuringDumb: PsiFile? = null
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            runReadAction {
                val libraryPsi = checkNotNull(PsiManager.getInstance(project).findFile(libraryFile))
                psiDuringDumb = libraryPsi
                val snapshot = LuaTypesSnapshot.forFile(libraryPsi)
                println(
                    "DR-05 while dumb: libDumb graph type = ${snapshot.getGlobalType("libDumb")} " +
                        "psi=${System.identityHashCode(
                            libraryPsi,
                        )} stamp=${libraryPsi.modificationStamp} valid=${libraryPsi.isValid}",
                )
            }
        }
        runReadAction {
            val reloaded = checkNotNull(PsiManager.getInstance(project).findFile(libraryFile))
            println(
                "DR-05 after dumb: psi=${System.identityHashCode(reloaded)} stamp=${reloaded.modificationStamp} " +
                    "sameInstance=${reloaded === psiDuringDumb} oldValid=${psiDuringDumb?.isValid} " +
                    "oldStamp=${psiDuringDumb?.modificationStamp} graph=${LuaTypesSnapshot.forFile(
                        reloaded,
                    ).getGlobalType("libDumb")}",
            )
        }

        val afterDumb = membersOfGlobal("libDumb", consumer)
        println("DR-05 after leaving dumb mode: libDumb members = $afterDumb")
        assertEquals(
            "a snapshot built while indexing must not keep resolveGlobal's nulls once the indexes are ready",
            setOf("fromLibrary"),
            afterDumb,
        )
    }

    /** Control — the same question with no dumb-mode phase at all. Isolates the dumb build as the cause. */
    fun testTheSameLibraryResolvesWhenNeverBuiltWhileDumb() {
        installDefinitionLibrary(
            "luassert",
            mapOf(
                "delta.lua" to "---@meta\n\nlibDumb = sharedByLibrary\n",
                "deltaSource.lua" to "---@meta\n\nsharedByLibrary = { fromLibrary = 1 }\n",
            ),
        )
        announceRootsChange()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        assertEquals(setOf("fromLibrary"), membersOfGlobal("libDumb", consumer))
    }
}
