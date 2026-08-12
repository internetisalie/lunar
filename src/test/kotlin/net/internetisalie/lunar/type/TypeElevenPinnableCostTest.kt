package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.platform.target.RuntimeLibraryProvider
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * TC-15 — **the pin still pays for itself**.
 *
 * TYPE-11-06 is closed by four conservative rules (design §3.3 steps 4–7), and every one of them
 * makes a file *less* pinnable. A rule set that closes the requirement by pinning **nothing** would
 * pass `TypeElevenDr01ResidualTest`, `TypeElevenDr11LateDeclarationTest`,
 * `TypeElevenDr12WarmInnerSnapshotTest`, `TypeElevenDr14InProgressTest` and
 * `TypeElevenDr15LateLibraryAnswerTest` — all five stale-type channels — while delivering exactly
 * nothing. Counting is the only way to tell the two apart, which is why the de-risking rounds each
 * carried a `REVIEW-COST TOTALS provisioned=11 … guarded=11` line and why that count is asserted
 * here rather than quoted.
 *
 * The enumerated count is asserted **first**, on purpose: a fixture that silently found zero
 * provisioned files satisfies "all of them are pinnable" vacuously.
 */
class TypeElevenPinnableCostTest : TypeElevenDefinitionLibraryTestCase() {
    fun testEveryFileShippedTodayIsStillPinnableInOneCleanEpoch() {
        // The default target is named rather than inherited: the light project's settings are shared
        // across test classes, and the 5.1 tree ships nine stubs where 5.4 ships ten.
        LuaProjectSettings.getInstance(project).state.setTarget(Target.default())
        val libraryRoot = installDefinitionLibrary("luassert", mapOf("wx.lua" to bigLibrary()))
        myFixture.configureByText("consumer.lua", "local pad = 1\n")
        val target = LuaProjectSettings.getInstance(project).state.getTarget()
        val bundledStubs = runReadAction { RuntimeLibraryProvider(project).getLibraryFiles(target) }
        val definitionFiles = libraryRoot.children.filter { it.extension == "lua" }
        val provisionedFiles = bundledStubs + definitionFiles

        assertEquals(
            "the fixture must enumerate the 10 bundled stdlib stubs plus the installed definition " +
                "library, or an all-pinnable verdict is vacuous (target=$target, " +
                "stubs=${bundledStubs.map { it.name }}, library=${definitionFiles.map { it.name }})",
            11,
            provisionedFiles.size,
        )

        val rejected = provisionedFiles.filterNot { isPinnableInThisEpoch(it) }

        println("TYPE11-COST provisioned=${provisionedFiles.size} pinnable=${provisionedFiles.size - rejected.size}")
        assertEquals(
            "every file this plugin provisions today was pinnable when the de-risking measured it " +
                "(guarded=11); a rule that closes TYPE-11-06 by pinning nothing is not a fix",
            emptyList<String>(),
            rejected.map { it.name },
        )
    }

    private fun isPinnableInThisEpoch(provisionedFile: VirtualFile): Boolean {
        val recordedFrame = frameOf(snapshotOf(provisionedFile))
        val verdict = runReadAction { LuaTypesSnapshot.isPinnable(psiFileOf(provisionedFile), recordedFrame) }
        if (!verdict) {
            println(
                "TYPE11-COST file=${provisionedFile.name} urls=${recordedFrame.urls} " +
                    "absences=${recordedFrame.absences} warm=${recordedFrame.unreplayedWarm} " +
                    "inProgress=${recordedFrame.inProgressHits} rescued=${recordedFrame.rescuedGlobals}",
            )
        }
        return verdict
    }
}
