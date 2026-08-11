package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex
import net.internetisalie.lunar.lang.psi.types.LuaLibraryProvenance
import net.internetisalie.lunar.platform.target.RuntimeLibraryProvider
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * TYPE-11-03's gate (design §8): the five DR-02 facts, asserted against the **production**
 * `LuaLibraryProvenance` service.
 *
 * `TypeElevenDr02ProvenanceTest` asserts the same facts against a predicate defined inside its own
 * file, matching by `VfsUtilCore.isAncestor` where design §3.2 specifies URL-prefix containment — so
 * every one of its ledger mutations mutated a **replica**, and no defect in the shipped service can
 * turn them red. That class stays as de-risking; this one is the acceptance, and each of its
 * assertions has been shown red against the real service by the mutation named in its KDoc.
 *
 * The library text is deliberately small. DR-02 used the 123 KiB `bigLibrary()` because it was also
 * measuring latency; provenance is a URL question and pays nothing for the size.
 */
class LuaLibraryProvenanceTest : TypeElevenDefinitionLibraryTestCase() {
    override fun setUp() {
        super.setUp()
        // The light project is shared across test methods, and so is the memoized root list hanging
        // off it. tearDown clears the enabled libraries but announces nothing, so without a tick
        // here a test could read the previous method's roots.
        announceRootsChange()
    }

    private fun provenance(): LuaLibraryProvenance = LuaLibraryProvenance.getInstance(project)

    private fun libraryText(): String = "---@meta\n\n---@class wx\nwx = {}\n\nreturn wx\n"

    /**
     * The exact discovery `LuaTypeManagerImpl.typeOfGlobalIn` performs before calling
     * `LuaTypesSnapshot.forFile` on each result — reproduced rather than described, because the
     * question is what the files it hands to `forFile` are classified as.
     */
    private fun filesDeclaringGlobal(name: String): List<VirtualFile> =
        FileBasedIndex
            .getInstance()
            .getContainingFiles(LuaGlobalAssignmentIndex.KEY, name, GlobalSearchScope.allScope(project))
            .toList()

    /**
     * TC-5. Mutation: drop `definitionRoots` from `computeRootUrls` → red on the `wx.lua` half.
     */
    fun testEveryFileResolveGlobalWouldVisitIsClassifiedByTheProductionService() {
        installDefinitionLibrary("luassert", mapOf("wx.lua" to libraryText()))
        myFixture.addFileToProject("projectGlobal.lua", "---@meta\nprojectOnly = {}\n")
        announceRootsChange()

        runReadAction {
            listOf("wx" to true, "projectOnly" to false).forEach { (name, expectProvisioned) ->
                val declaringFiles = filesDeclaringGlobal(name)
                assertFalse(
                    "no file declares the global '$name' — the fixture is not exercising anything",
                    declaringFiles.isEmpty(),
                )
                declaringFiles.forEach { virtualFile ->
                    assertEquals(
                        "provenance must classify ${virtualFile.path} as provisioned=$expectProvisioned",
                        expectProvisioned,
                        provenance().isProvisionedUrl(virtualFile.url),
                    )
                }
            }
        }
    }

    /**
     * The bundled stdlib is provenance's other source, and it arrives over a different file system
     * (`jar://` in a shipped plugin) than the definition libraries do.
     *
     * Mutation: drop `runtimeRoot` from `computeRootUrls` → red.
     */
    fun testEveryBundledRuntimeStubFileIsProvisioned() {
        val target = LuaProjectSettings.getInstance(project).state.getTarget()
        val runtimeFiles = RuntimeLibraryProvider(project).getLibraryFiles(target)
        assertFalse(
            "the bundled runtime tree contributed no files for $target — provenance would have one " +
                "source and this case would pass vacuously",
            runtimeFiles.isEmpty(),
        )

        runReadAction {
            runtimeFiles.forEach { stubFile ->
                assertTrue(
                    "the bundled stub ${stubFile.url} must be provisioned",
                    provenance().isProvisionedUrl(stubFile.url),
                )
            }
        }
    }

    /**
     * TC-7 in the form that has a production entry point: the seeded tree can only be provisioned if
     * the **EP-registered** `LuaDefinitionLibraryProvider` contributed it. A locally constructed
     * provider would pass this while the feature was dead in a running IDE.
     *
     * Mutation: clear the enabled-library list after install (and tick roots) → red.
     */
    fun testTheSeededLibraryIsProvisionedThroughTheRegisteredProvider() {
        val root = installDefinitionLibrary("luassert", mapOf("wx.lua" to libraryText()))
        val libraryVirtualFile = checkNotNull(root.findChild("wx.lua")) { "the seeded library file is missing" }

        runReadAction {
            assertTrue("the seeded library root itself must be provisioned", provenance().isProvisionedUrl(root.url))
            assertTrue(
                "a file inside the seeded library root must be provisioned",
                provenance().isProvisionedUrl(libraryVirtualFile.url),
            )
        }
    }

    /**
     * TC-6, in the `PsiFile`-overload form — the only one §2.2 exposes. The `true` is load-bearing
     * *because* the copy has no `virtualFile` of its own: it can be answered only through
     * `originalFile`.
     *
     * Mutation: read `psiFile.virtualFile` instead of `psiFile.originalFile.virtualFile` → red.
     */
    fun testACopyOfALibraryFileIsProvisionedOnlyThroughOriginalFile() {
        val root = installDefinitionLibrary("luassert", mapOf("wx.lua" to libraryText()))
        val libraryVirtualFile = checkNotNull(root.findChild("wx.lua")) { "the seeded library file is missing" }

        runReadAction {
            val libraryPsi = checkNotNull(PsiManager.getInstance(project).findFile(libraryVirtualFile))
            val copy = libraryPsi.copy() as PsiFile
            assertNull(
                "a copy is expected to carry no virtualFile of its own; without that this case " +
                    "would not distinguish the two accessors",
                copy.virtualFile,
            )
            assertTrue("a completion copy of a library file must be provisioned", provenance().isProvisioned(copy))
        }
    }

    /**
     * Mutation: widen `computeRootUrls` with the project's own base URL → red.
     */
    fun testALightFixtureProjectFileIsNeverProvisioned() {
        installDefinitionLibrary("luassert", mapOf("wx.lua" to libraryText()))
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\nwx.wxC_0 = 2\n")

        runReadAction {
            assertFalse("a project file must not be provisioned", provenance().isProvisioned(consumer))
            assertFalse(
                "a project file must not be provisioned through originalFile either",
                provenance().isProvisioned(consumer.originalFile),
            )
        }
    }

    /**
     * The `"$root/"` suffix in the prefix test, which design §3.2 calls out as required rather than
     * cosmetic. A cache directory is `<id>-<version>`, so sibling roots sharing a prefix are the
     * normal shape of that tree, not a contrived one.
     *
     * Mutation: `url.startsWith(it)` without the separator → red.
     */
    fun testASiblingRootSharingAPrefixIsNotProvisioned() {
        val root = installDefinitionLibrary("luassert", mapOf("wx.lua" to libraryText()))

        runReadAction {
            assertFalse(
                "a URL that merely extends a provisioned root's URL is a different root",
                provenance().isProvisionedUrl("${root.url}extended/wx.lua"),
            )
        }
    }
}
