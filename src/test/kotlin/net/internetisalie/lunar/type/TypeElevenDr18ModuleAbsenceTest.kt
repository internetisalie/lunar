package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IndexingTestUtil
import net.internetisalie.lunar.lang.path.resolveModuleCandidates
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager

/**
 * TC-20 / TYPE-11-DR-18 — §1.8 B1's shape in the **module** door, the one door the absence question
 * was never put to (design §1.12).
 *
 * `doResolveModule` answers `LuaPrimitiveType.ANY` when nothing provides the module, and `ANY` is
 * **non-null**: the visitor embeds it, `resolveModule` caches it, and — without §3.1 step 5d — the
 * library file that asked records an entirely **empty** frame, clears §3.3 steps 2–7 and is pinned.
 * Creating the module afterwards never reaches it, because `MODIFICATION_COUNT` is precisely the
 * dependency the pin removed.
 *
 * Reachability on what ships today is likely zero — no bundled stub or `---@meta` addon uses
 * `require`. V1 and V2 were equally unreachable and were fixed anyway, on this design's rule that a
 * pin must be correct at the moment it is taken; there is no second chance.
 *
 * ⚠ **This case asserts the user-visible answer; it does not attribute it.** Measured on the shipped
 * build: with §3.1 step 5d deleted it stays **green**. A library file that calls `require` resolves
 * the global `require` itself through the all-scope fallback — probed, `mu.lua`'s frame is
 * `urls=[package.lua] absences=[module:mymod] rescued=[global:require]` — so §3.3 step 7 denies the
 * pin whatever the module rules do. The attributable gate for step 5d is
 * `LuaTypeManagerRecordingTest.testAModuleThatResolvesToNothingIsRecordedAsAnAbsence`, which is red
 * under exactly that mutation.
 */
class TypeElevenDr18ModuleAbsenceTest : TypeElevenDefinitionLibraryTestCase() {
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

    fun testAModuleCreatedAfterTheLibrarySnapshotWasBuiltStillReachesIt() {
        installDefinitionLibrary("luassert", mapOf("mu.lua" to "---@meta\n\nmuAlias = require(\"mymod\")\n"))
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")
        val rootsTracker = ProjectRootModificationTracker.getInstance(project)

        assertEquals(
            "the premise of this test is that NOTHING provides the module when the snapshot is built",
            emptyList<String>(),
            runReadAction { resolveModuleCandidates(project, "mymod").map { it.name }.toList() },
        )
        val before = membersOfGlobal("muAlias", consumer)
        println("DR-18 before: muAlias = $before")
        val rootsBefore = rootsTracker.modificationCount

        myFixture.addFileToProject("mymod.lua", "return { fromModule = 1 }\n")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val after = membersOfGlobal("muAlias", consumer)
        println("DR-18 after: muAlias = $after roots $rootsBefore -> ${rootsTracker.modificationCount}")
        assertEquals(
            "creating the module must not be a roots change; a verdict from a ticked roots tracker " +
                "would be about that tick, not about the recorded-nothing case",
            rootsBefore,
            rootsTracker.modificationCount,
        )
        assertEquals(
            "a module created AFTER the library snapshot was built must still reach it",
            setOf("fromModule"),
            after,
        )
    }
}
