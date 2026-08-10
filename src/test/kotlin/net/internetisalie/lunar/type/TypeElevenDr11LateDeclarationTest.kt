package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager

/**
 * TYPE-11 DR-11 — the **absence** residual (Step 9 blocker B1), as an assertion rather than a
 * paragraph.
 *
 * Design §3.1/§3.5 record the files a resolution *visited*. A free global that nothing declares is
 * visited nowhere, so the recorded set is **empty**, and §3.3 step 3 (`sources.any { !provisioned }`)
 * is vacuously satisfied — the file is judged maximally pinnable at the exact moment its type is
 * least trustworthy. Design §3.4 identifies this inversion for dumb mode and does not generalise it
 * to a failed smart-mode resolution.
 *
 * §3.3 asserts a pinned file "is re-judged on its next build, which the global tracker guarantees
 * happens". For a pinned file that sentence is false: `PsiModificationTracker.MODIFICATION_COUNT` is
 * precisely the dependency the pin removes.
 *
 * Like `TypeElevenDr01ResidualTest`, this asserts **today's correct answer** and is green on `main`.
 * It goes red under the §3 conditional rule as written, and that is the measurement.
 */
class TypeElevenDr11LateDeclarationTest : TypeElevenDefinitionLibraryTestCase() {
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

    private fun filesDeclaring(name: String): List<String> =
        runReadAction {
            FileBasedIndex
                .getInstance()
                .getContainingFiles(LuaGlobalAssignmentIndex.KEY, name, GlobalSearchScope.allScope(project))
                .map { it.name }
        }

    fun testADeclarationWrittenAfterTheLibrarySnapshotWasBuiltStillReachesIt() {
        installDefinitionLibrary(
            "luassert",
            mapOf("alpha.lua" to "---@meta\n\nlibAlias = sharedByProject\n"),
        )
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")
        val rootsTracker = ProjectRootModificationTracker.getInstance(project)

        assertEquals(
            "the premise of this test is that NOTHING declares the global when the snapshot is built",
            emptyList<String>(),
            filesDeclaring("sharedByProject"),
        )
        val before = membersOfGlobal("libAlias", consumer)
        println("DR-11 before: libAlias = $before declaring = ${filesDeclaring("sharedByProject")}")
        val rootsBefore = rootsTracker.modificationCount

        myFixture.addFileToProject("shared.lua", "sharedByProject = { afterDeclared = 1 }\n")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val after = membersOfGlobal("libAlias", consumer)
        println(
            "DR-11 after: libAlias = $after declaring = ${filesDeclaring("sharedByProject")} " +
                "roots $rootsBefore -> ${rootsTracker.modificationCount}",
        )
        assertEquals(
            "adding the declaration must not be a roots change; a verdict from a ticked roots " +
                "tracker would be about that tick, not about the recorded-nothing case",
            rootsBefore,
            rootsTracker.modificationCount,
        )
        assertEquals(
            "a project declaration written AFTER the library snapshot was built must still reach it",
            setOf("afterDeclared"),
            after,
        )
    }
}
