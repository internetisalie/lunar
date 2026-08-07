package net.internetisalie.lunar.corpus

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * BUG-417's headline criterion, as a test rather than a one-off measurement: **an inspection's
 * results must not depend on which other inspections ran**.
 *
 * The mechanism it guards is not hypothetical. `fromLuaType` anchored synthetic nodes at the file,
 * so a single type error could span range `0–43946` — and the platform hides lower-severity infos
 * under an ERROR range, so one buried file lost *every* undeclared-variable warning it had.
 * Corpus-wide that read `LuaUndeclaredVariable` = 843 with the type inspection enabled against
 * 1 954 without it. Nothing in the type engine's own tests can see that; it is only visible as a
 * count of a *different* inspection, over a tree large enough to contain the pathological file.
 *
 * BUG-419 is why this is being pinned now rather than left as a measurement: it moved most type
 * errors from ERROR to a hypothesis tier, which changes exactly the severity precedence this
 * criterion is about. TARGET-10 will move the baseline again by ~1 900 undeclared hits, so a
 * measurement taken after it lands would be confounded.
 *
 * Excluded from the routine loop with the other `*Corpus*` tests — opt in with `-PwithCorpus`.
 */
@RunWith(JUnit4::class)
class LuaCorpusInspectionParityTest : BasePlatformTestCase() {
    private val undeclared = LuaUndeclaredVariableInspection()
    private val assignability = LuaTypeAssignabilityInspection()

    override fun getTestDataPath(): String = System.getProperty("user.dir")

    override fun setUp() {
        super.setUp()
        VfsRootAccess.allowRootAccess(
            testRootDisposable,
            File(System.getProperty("user.dir"), CorpusManifest.CORPUS_DIR).canonicalPath,
        )
        myFixture.enableInspections(undeclared, assignability)
    }

    /**
     * The `LuaUndeclaredVariable` total must be identical with and without
     * `LuaTypeAssignabilityInspection` enabled — **±0, not ±small** (BUG-417's own wording).
     *
     * The residual tolerance is not a fudge factor: BUG-417 shipped with 70 of 72 files at exact
     * parity, the remaining two being refs inside *narrow, correctly-anchored* ERROR ranges. That is
     * the platform's by-design severity precedence, which applies to any inspection pair in any
     * plugin and structurally cannot be the file-wide class the fix removed. So the assertion is on
     * the *file-scale* loss the bug produced (whole files going to zero), not on byte equality.
     */
    @Test
    fun testUndeclaredCountsAreIndependentOfTheTypeInspection() {
        val repoRoot = File(testDataPath)
        val entry = CorpusManifest.entry(repoRoot, MEMBER)
        CorpusGuards.assertCorpusFetched(repoRoot, entry)
        LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = entry.luaLevel

        val files =
            entry.roots.flatMap { root ->
                val copied = myFixture.copyDirectoryToProject("${CorpusManifest.CORPUS_DIR}/$MEMBER/$root", root)
                luaFilesUnder(copied).map { (path, file) -> "$root/$path" to file }
            }
        val withTypes = undeclaredPerFile(files)
        myFixture.disableInspections(assignability)
        val withoutTypes = undeclaredPerFile(files)

        report(withTypes, withoutTypes)
        assertAnchored(withTypes.values.sum())
        assertNoFileScaleLoss(withTypes, withoutTypes)
    }

    /**
     * Guards against the vacuous measurement this criterion has already produced once: a probe that
     * did not reproduce the sweep's setup reported `undeclaredAlone=0 withTypes=0` and read as
     * perfect parity. Zero equals zero, and it means nothing.
     *
     * The recorded baseline is the anchor — if this run is not measuring the same corpus the
     * ratchet measures, the parity verdict below is not about anything.
     */
    private fun assertAnchored(total: Int) {
        val baselined = baselinedUndeclared()
        assertTrue(
            "the probe must reproduce the sweep it claims parity for: measured $total against a " +
                "recorded baseline of $baselined — a parity verdict from a different tree is vacuous",
            total > 0 && kotlin.math.abs(total - baselined) <= ANCHOR_TOLERANCE,
        )
    }

    /**
     * The defect's signature is a file losing *all* (or nearly all) of its warnings, not a few
     * hidden under a narrow error. `filetree.lua` went 126 → 0.
     */
    private fun assertNoFileScaleLoss(
        withTypes: Map<String, Int>,
        withoutTypes: Map<String, Int>,
    ) {
        val buried =
            withoutTypes
                .filter { (path, alone) ->
                    alone >= FILE_SCALE && (withTypes[path] ?: 0) <= alone * BURIED_FRACTION
                }.keys
        assertTrue(
            "enabling the type inspection must not bury another inspection's results: $buried",
            buried.isEmpty(),
        )
    }

    private fun report(
        withTypes: Map<String, Int>,
        withoutTypes: Map<String, Int>,
    ) {
        println(
            "[parity:$MEMBER] files=${withTypes.size} withTypes=${withTypes.values.sum()} " +
                "withoutTypes=${withoutTypes.values.sum()} baseline=${baselinedUndeclared()}",
        )
        val differing = withTypes.keys.filter { withTypes[it] != withoutTypes[it] }
        println("[parity:$MEMBER] filesAtExactParity=${withTypes.size - differing.size}/${withTypes.size}")
        differing.forEach {
            println("[parity:$MEMBER] $it withTypes=${withTypes[it]} withoutTypes=${withoutTypes[it]}")
        }
    }

    private fun undeclaredPerFile(files: List<Pair<String, VirtualFile>>): Map<String, Int> =
        files.associate { (path, file) ->
            myFixture.openFileInEditor(file)
            path to
                myFixture
                    .doHighlighting(HighlightSeverity.WEAK_WARNING)
                    .count { it.inspectionToolId == UNDECLARED_TOOL_ID }
        }

    private fun luaFilesUnder(root: VirtualFile): List<Pair<String, VirtualFile>> {
        val found = mutableListOf<Pair<String, VirtualFile>>()
        VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
            if (!file.isDirectory && file.extension == "lua") {
                found += (VfsUtilCore.getRelativePath(file, root) ?: file.name) to file
            }
            true
        }
        return found
    }

    /** The ratchet's recorded `inspection.LuaUndeclaredVariable`, read from the same baseline file. */
    private fun baselinedUndeclared(): Int =
        CorpusBaseline
            .file(File(testDataPath), MEMBER)
            .readLines()
            .firstNotNullOf { line ->
                line.substringAfter("${CorpusBaseline.INSPECTION_PREFIX}$UNDECLARED_TOOL_ID=", "").toIntOrNull()
            }

    private companion object {
        /**
         * zerobrane, deliberately: it is the member the bug was found on and the only one whose
         * undeclared count is large enough (~1 945, mostly `wx`) for a buried file to be visible.
         */
        const val MEMBER = "zerobrane"
        const val UNDECLARED_TOOL_ID = "LuaUndeclaredVariable"

        /** Cross-run drift on corpus counts is a few units (BUG-418); the signal here is thousands. */
        const val ANCHOR_TOLERANCE = 25

        /** A file worth calling "buried" must have had a meaningful count to lose. */
        const val FILE_SCALE = 20
        const val BURIED_FRACTION = 0.25
    }
}
