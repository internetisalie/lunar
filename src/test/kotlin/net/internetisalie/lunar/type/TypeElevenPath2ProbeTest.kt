package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * TYPE-11 probe — the printing harness that decided the shape of three claims before any of them was
 * asserted. Kept rather than deleted because each finding contradicted a written statement, and the
 * next person to touch this area will want the raw output rather than the summary.
 *
 * It has **no assertions and is not a gate**. Read `design.md` §1.2, §1.3 and §1.6 for what its output
 * established.
 */
class TypeElevenPath2ProbeTest : TypeElevenDefinitionLibraryTestCase() {
    private fun graphTypeOfGlobal(
        name: String,
        label: String,
    ) {
        FileBasedIndex
            .getInstance()
            .getContainingFiles(LuaGlobalAssignmentIndex.KEY, name, GlobalSearchScope.allScope(project))
            .forEach { virtualFile ->
                val psi = PsiManager.getInstance(project).findFile(virtualFile)
                val graph = psi?.let { LuaTypesSnapshot.forFile(it).getGlobalType(name) }
                println("$label global='$name' in ${virtualFile.name}: graphType=$graph")
            }
    }

    private fun membersOfGlobal(name: String): String {
        val consumer = myFixture.configureByText("probe.lua", "local pad = 1\n")
        val members =
            LuaTypeManager
                .getInstance(project)
                .resolveGlobal(name, consumer)
                ?.getMembers()
                ?.keys
        return "$members"
    }

    /** Which door carries a project-declared method onto a stub-defined class? */
    fun testProbePath2Doors() {
        installDefinitionLibrary(
            "luassert",
            mapOf(
                "beta.lua" to "---@meta\n\n---@class LibClass\nLibClass = {}\n",
                "beta2.lua" to "---@meta\n\nlibHandle = LibClass\n",
                "beta3.lua" to "---@meta\n\n---@type LibClass\nlibTyped = nil\n",
            ),
        )
        myFixture.addFileToProject("ext.lua", "function LibClass:beforeEdit() end\n")
        announceRootsChange()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")
        val manager = LuaTypeManager.getInstance(project)

        runReadAction {
            println("P2 resolveGlobal(LibClass) = ${manager.resolveGlobal("LibClass", consumer)?.getMembers()?.keys}")
            println("P2 resolveType(LibClass)   = ${manager.resolveType("LibClass", consumer)?.getMembers()?.keys}")
            println("P2 resolveGlobal(libHandle)= ${manager.resolveGlobal("libHandle", consumer)?.getMembers()?.keys}")
            println("P2 resolveGlobal(libTyped) = ${manager.resolveGlobal("libTyped", consumer)?.getMembers()?.keys}")
            listOf("LibClass", "libHandle", "libTyped").forEach { graphTypeOfGlobal(it, "P2") }
        }
    }

    /** The hosted `---@class` form: does a className reach the graph, carrying project members with it? */
    fun testProbeHostedClassShape() {
        installDefinitionLibrary(
            "luassert",
            mapOf(
                "host.lua" to "---@meta\n\n---@class HostClass\nlocal HostClass = {}\nHostGlobal = HostClass\n",
                "host2.lua" to "---@meta\n\nlibHandle2 = HostGlobal\n",
            ),
        )
        myFixture.addFileToProject("ext.lua", "function HostClass:beforeEdit() end\n")
        announceRootsChange()

        runReadAction {
            println("P2H resolveGlobal(HostGlobal) = ${membersOfGlobal("HostGlobal")}")
            println("P2H resolveGlobal(libHandle2) = ${membersOfGlobal("libHandle2")}")
            listOf("HostGlobal", "libHandle2").forEach { graphTypeOfGlobal(it, "P2H") }
        }
    }

    /** Is a library snapshot itself stale after a project edit, or does something re-merge at read time? */
    fun testProbeSnapshotStalenessAfterAProjectEdit() {
        val root =
            installDefinitionLibrary(
                "luassert",
                mapOf(
                    "host.lua" to "---@meta\n\n---@class HostClass\nlocal HostClass = {}\nHostGlobal = HostClass\n",
                    "host2.lua" to "---@meta\n\nlibHandle = HostGlobal\n",
                ),
            )
        val extension = myFixture.addFileToProject("ext.lua", "function HostClass:beforeEdit() end\n")
        announceRootsChange()
        val host2 = checkNotNull(root.findChild("host2.lua"))
        val rootsTracker = ProjectRootModificationTracker.getInstance(project)

        runReadAction {
            val psi = checkNotNull(PsiManager.getInstance(project).findFile(host2))
            println("P2S before: graph=${LuaTypesSnapshot.forFile(psi).getGlobalType("libHandle")}")
            println("P2S before: resolveGlobal=${membersOfGlobal("libHandle")}")
            println("P2S before: rootsTracker=${rootsTracker.modificationCount}")
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val documentManager = PsiDocumentManager.getInstance(project)
            val document = checkNotNull(documentManager.getDocument(extension))
            document.setText("function HostClass:afterEdit() end\n")
            documentManager.commitDocument(document)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        runReadAction {
            val psi = checkNotNull(PsiManager.getInstance(project).findFile(host2))
            println("P2S after: graph=${LuaTypesSnapshot.forFile(psi).getGlobalType("libHandle")}")
            println("P2S after: resolveGlobal=${membersOfGlobal("libHandle")}")
            println("P2S after: rootsTracker=${rootsTracker.modificationCount}")
        }
    }

    /**
     * Does the roots tracker tick across a dumb-mode episode? If it did, a generation-pinned snapshot
     * would be healed by the tick rather than by the guard, and DR-05 could not distinguish them.
     */
    fun testProbeRootsTrackerAcrossDumbMode() {
        installDefinitionLibrary("luassert", mapOf("delta.lua" to "---@meta\n\nlibDumb = sharedByProject\n"))
        myFixture.addFileToProject("shared.lua", "sharedByProject = { fromProject = 1 }\n")
        announceRootsChange()
        val rootsTracker = ProjectRootModificationTracker.getInstance(project)
        val psiTracker = PsiModificationTracker.getInstance(project)

        println("P2D before dumb: roots=${rootsTracker.modificationCount} psi=${psiTracker.modificationCount}")
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            println("P2D inside dumb: roots=${rootsTracker.modificationCount} psi=${psiTracker.modificationCount}")
        }
        println("P2D after dumb: roots=${rootsTracker.modificationCount} psi=${psiTracker.modificationCount}")
    }

    /** Is `PsiManager.findFile(vf).virtualFile` the same object, and the same value, as `vf`? */
    fun testProbeVirtualFileIdentity() {
        installDefinitionLibrary("luassert", mapOf("wx.lua" to "---@meta\n\nwx = { a = 1 }\n"))
        myFixture.addFileToProject("projectGlobal.lua", "projectOnly = { b = 1 }\n")
        announceRootsChange()

        runReadAction {
            listOf("wx", "projectOnly").forEach { name ->
                FileBasedIndex
                    .getInstance()
                    .getContainingFiles(LuaGlobalAssignmentIndex.KEY, name, GlobalSearchScope.allScope(project))
                    .forEach { virtualFile ->
                        val psi = PsiManager.getInstance(project).findFile(virtualFile)
                        println(
                            "P2-ID '$name' vf=${virtualFile.path} class=${virtualFile.javaClass.simpleName} " +
                                "psiVfClass=${psi?.virtualFile?.javaClass?.simpleName} " +
                                "tripleEq=${psi?.virtualFile === virtualFile} " +
                                "doubleEq=${psi?.virtualFile == virtualFile}",
                        )
                    }
            }
        }
    }
}
