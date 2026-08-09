package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryProvider
import net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex
import net.internetisalie.lunar.platform.target.RuntimeLibraryProvider
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * TYPE-11 DR-02 — can every file `resolveGlobal` resolves into be matched by `VirtualFile` identity
 * against the provenance set?
 *
 * The candidate predicate is spelled out here rather than imported, so this file is the executable
 * spec `design.md` §3 transcribes. It composes the two plugin-provisioned sources and **only** those:
 *
 * - `RuntimeLibraryProvider.getLibraryRoot(target)` — the bundled stdlib/platform stub tree. It is
 *   the single root both `LuaLibraryProvider` and `PlatformLibraryProvider.getSupportLibraries` are
 *   built from, so matching it covers both without asking either.
 * - the EP-registered `LuaDefinitionLibraryProvider`'s `getRootsToWatch` — the enabled **and fetched**
 *   definition libraries, from the production instance with the production fetcher.
 *
 * Deliberately absent: `LuaRocksLibraryProvider` (out of v1 scope) and
 * `PlatformLibraryProvider.getExternalLibraries`, whose "Search Trees" are arbitrary user source
 * paths from `PathConfiguration.getStaticSourcePathPatterns` — mutable project-adjacent code, not a
 * plugin-provisioned immutable library.
 */
class TypeElevenDr02ProvenanceTest : TypeElevenDefinitionLibraryTestCase() {
    private fun provenanceRoots(targetProject: Project): List<VirtualFile> {
        val target = LuaProjectSettings.getInstance(targetProject).state.getTarget()
        val runtimeRoot = RuntimeLibraryProvider(targetProject).getLibraryRoot(target)
        val definitionRoots =
            AdditionalLibraryRootsProvider.EP_NAME.extensionList
                .filterIsInstance<LuaDefinitionLibraryProvider>()
                .flatMap { it.getRootsToWatch(targetProject) }
        return listOfNotNull(runtimeRoot) + definitionRoots
    }

    private fun isProvisioned(file: VirtualFile?): Boolean {
        val candidate = file ?: return false
        return provenanceRoots(project).any { VfsUtilCore.isAncestor(it, candidate, false) }
    }

    /**
     * The exact discovery `LuaTypeManagerImpl.typeOfGlobalIn` performs before calling
     * `LuaTypesSnapshot.forFile` on each result — reproduced rather than described, because the
     * question is what identity the files it hands to `forFile` actually carry.
     */
    private fun filesDeclaringGlobal(name: String): List<VirtualFile> =
        FileBasedIndex
            .getInstance()
            .getContainingFiles(LuaGlobalAssignmentIndex.KEY, name, GlobalSearchScope.allScope(project))
            .toList()

    /**
     * Live binding first: if the EP-registered provider does not contribute the seeded tree, every
     * other assertion in this class is measuring a fixture instead of the product.
     */
    fun testTheSeededLibraryReachesTheProjectThroughTheRegisteredProvider() {
        val root = installDefinitionLibrary("luassert", mapOf("wx.lua" to bigLibrary()))

        val registeredRoots =
            AdditionalLibraryRootsProvider.EP_NAME.extensionList
                .filterIsInstance<LuaDefinitionLibraryProvider>()
                .flatMap { it.getRootsToWatch(project) }

        println("DR-02 registered definition roots = ${registeredRoots.map { it.path }}")
        assertTrue(
            "the EP-registered LuaDefinitionLibraryProvider must contribute the seeded root; " +
                "got $registeredRoots",
            registeredRoots.any { it == root },
        )
    }

    /** The bundled stdlib root is the other provenance source; report the URL scheme it arrives as. */
    fun testTheBundledRuntimeRootIsVisibleAndItsSchemeIsRecorded() {
        val target = LuaProjectSettings.getInstance(project).state.getTarget()
        val runtimeRoot = RuntimeLibraryProvider(project).getLibraryRoot(target)
        println("DR-02 target=$target runtimeRoot=${runtimeRoot?.url} fileSystem=${runtimeRoot?.fileSystem?.protocol}")
        println("DR-02 runtimeRoot children = ${runtimeRoot?.children?.map { it.name }?.sorted()}")
        assertNotNull("the bundled runtime library root must resolve, or provenance has one source", runtimeRoot)
    }

    /** The core DR-02 question: every declaring file is classified, and classified correctly. */
    fun testEveryFileResolveGlobalWouldVisitIsClassifiedByProvenance() {
        installDefinitionLibrary("luassert", mapOf("wx.lua" to bigLibrary()))
        myFixture.addFileToProject("projectGlobal.lua", "---@meta\nprojectOnly = {}\n")
        announceRootsChange()

        runReadAction {
            listOf("wx" to true, "projectOnly" to false).forEach { (name, expectProvisioned) ->
                val files = filesDeclaringGlobal(name)
                assertFalse(
                    "no file declares the global '$name' — the fixture is not exercising anything",
                    files.isEmpty(),
                )
                files.forEach { virtualFile ->
                    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                    println(
                        "DR-02 global='$name' vf=${virtualFile.path} fs=${virtualFile.fileSystem.protocol} " +
                            "psi=${psiFile?.name} psiVf===vf:${psiFile?.virtualFile === virtualFile} " +
                            "originalVf===vf:${psiFile?.originalFile?.virtualFile === virtualFile} " +
                            "provisioned=${isProvisioned(virtualFile)}",
                    )
                    assertEquals(
                        "provenance must classify ${virtualFile.path} as provisioned=$expectProvisioned",
                        expectProvisioned,
                        isProvisioned(virtualFile),
                    )
                }
            }
        }
    }

    /**
     * The identity trap that decides which accessor the production predicate must use. A completion
     * copy is a `LightVirtualFile` outside every root, so a predicate reading `psiFile.virtualFile`
     * classifies a copy of a library file as **not** provisioned while one reading
     * `psiFile.originalFile.virtualFile` classifies it correctly.
     */
    fun testACopyOfALibraryFileIsOnlyMatchedThroughOriginalFile() {
        val root = installDefinitionLibrary("luassert", mapOf("wx.lua" to bigLibrary()))
        val libraryVirtualFile = checkNotNull(root.findChild("wx.lua"))

        runReadAction {
            val libraryPsi = checkNotNull(PsiManager.getInstance(project).findFile(libraryVirtualFile))
            val copy = libraryPsi.copy() as PsiFile
            println(
                "DR-02 copy: virtualFile=${copy.virtualFile} " +
                    "originalFile.virtualFile=${copy.originalFile.virtualFile?.path} " +
                    "byVirtualFile=${isProvisioned(copy.virtualFile)} " +
                    "byOriginalFile=${isProvisioned(copy.originalFile.virtualFile)}",
            )
            assertTrue(
                "the original of a copied library file must be provisioned",
                isProvisioned(copy.originalFile.virtualFile),
            )
            assertFalse(
                "the copy's own virtualFile must NOT match — this is the half that makes the accessor " +
                    "choice load-bearing rather than cosmetic",
                isProvisioned(copy.virtualFile),
            )
        }
    }

    /** A light-fixture project file must never be classified as provisioned, whichever accessor is used. */
    fun testALightFixtureProjectFileIsNeverProvisioned() {
        installDefinitionLibrary("luassert", mapOf("wx.lua" to bigLibrary()))
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\nwx.wxC_0 = 2\n")

        runReadAction {
            println(
                "DR-02 consumer: vf=${consumer.virtualFile?.path} fs=${consumer.virtualFile?.fileSystem?.protocol} " +
                    "original=${consumer.originalFile.virtualFile?.path} " +
                    "byVirtualFile=${isProvisioned(consumer.virtualFile)} " +
                    "byOriginalFile=${isProvisioned(consumer.originalFile.virtualFile)}",
            )
            assertFalse("a project file must not be provisioned", isProvisioned(consumer.virtualFile))
            assertFalse(
                "a project file must not be provisioned via originalFile either",
                isProvisioned(consumer.originalFile.virtualFile),
            )
        }
    }
}
