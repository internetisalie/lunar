package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import java.io.File
import java.nio.file.Files

/**
 * TARGET-00-DR-03 spike. Proves the load-bearing assumption of TARGET-08-04 **before** the provider
 * is written: that a directory exposed as a `SyntheticLibrary` source root actually gets indexed,
 * so its `---@meta` definitions resolve.
 *
 * This is not test-precedented in this repo. `LuaRocksLibraryProviderTest` calls
 * `getAdditionalProjectLibraries(project)` *directly* and asserts only the returned roots — it
 * never registers through the EP, and its `isInLibrary` assertion is commented out
 * (`LuaRocksLibraryProviderTest.kt:112-113`). So nothing here has ever shown that the platform
 * picks such a root up, which is exactly what Phase 4 depends on.
 *
 * Deliberately a throwaway provider rather than `LuaDefinitionLibraryProvider`: the point is to
 * capture the fixture plumbing (root access, root-change event, refresh) that TC 6 will need, with
 * nothing of Phase 4 written yet to confound a failure.
 */
class Dr03SyntheticLibraryResolutionSpikeTest : BasePlatformTestCase() {

    /** Minimal stand-in for the Phase 4 provider — one directory, exposed as a source root. */
    private class SpikeProvider(private val root: VirtualFile) : AdditionalLibraryRootsProvider() {
        override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> =
            listOf(object : SyntheticLibrary() {
                override fun getSourceRoots(): Collection<VirtualFile> = listOf(root)
                override fun equals(other: Any?): Boolean = other === this
                override fun hashCode(): Int = root.hashCode()
            })

        override fun getRootsToWatch(project: Project): Collection<VirtualFile> = listOf(root)
    }

    /** Writes a `---@meta` definition tree on the real filesystem and returns its VirtualFile. */
    private fun seedDefinitionTree(): VirtualFile {
        val dir = Files.createTempDirectory("lunar-dr03").toFile()
        val library = File(dir, "library").apply { mkdirs() }
        File(library, "spike.lua").writeText(
            """
            ---@meta

            ---A probe symbol that exists only inside the synthetic library.
            ---@param name string
            function dr03_probe(name) end
            """.trimIndent(),
        )
        // Light fixtures forbid reading outside the project tree unless the root is allowed.
        VfsRootAccess.allowRootAccess(testRootDisposable, dir.absolutePath)
        val virtual = VfsUtil.findFileByIoFile(library, true)
        checkNotNull(virtual) { "definition tree not visible to the VFS at ${library.absolutePath}" }
        VfsUtil.markDirtyAndRefresh(false, true, true, virtual)
        return virtual
    }

    private fun registerProvider(root: VirtualFile) {
        AdditionalLibraryRootsProvider.EP_NAME.point
            .registerExtension(SpikeProvider(root), testRootDisposable)
        // Registering the EP is not enough on its own: the platform caches its root set, so the
        // change has to be announced before anything indexes the new tree.
        runWriteAction {
            ProjectRootManagerEx.getInstanceEx(project)
                .makeRootsChange(EmptyRunnable.getInstance(), false, true)
        }
        // A roots change alone leaves the new tree unindexed in a light fixture: isInLibrary goes
        // true immediately, but the stub index is only populated once the rescan it schedules has
        // actually run. Without this wait the index is empty and every resolution assertion fails.
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    fun testSyntheticLibraryRootIsIndexedAndResolves() {
        val root = seedDefinitionTree()
        registerProvider(root)

        val probe = root.findChild("spike.lua")
        assertNotNull("the seeded definition file must be visible", probe)

        // Step 1 — does the platform consider it library content at all?
        val inLibrary = ProjectFileIndex.getInstance(project).isInLibrary(probe!!)
        assertTrue(
            "SyntheticLibrary root was registered but ProjectFileIndex does not see it as library " +
                "content; TARGET-08-04 cannot work as designed without this",
            inLibrary,
        )

        // Step 2 — is the definition actually in the stub index? This is the question Phase 4
        // depends on; completion is only one consumer of the answer.
        val indexed = runReadAction {
            StubIndex.getElements(
                LuaGlobalDeclarationIndex.KEY,
                "dr03_probe",
                project,
                GlobalSearchScope.allScope(project),
                LuaFuncDecl::class.java,
            ).map { it.containingFile.virtualFile.path }
        }
        assertTrue(
            "dr03_probe is not in LuaGlobalDeclarationIndex; the SyntheticLibrary root is library " +
                "content but its contents were never indexed. Indexed paths: $indexed",
            indexed.isNotEmpty(),
        )

        // Step 3 — completion is NOT asserted here, deliberately. It returns nothing for this
        // symbol even with the index populated, because `GlobalSymbolRankingService` searches
        // `GlobalSearchScope.projectScope` (:110, :180), which excludes library files by
        // definition. That is a Phase 4 work item recorded in risks-and-gaps, not a fixture
        // problem — asserting the current (wrong) behaviour here would lock the defect in.
    }
}
