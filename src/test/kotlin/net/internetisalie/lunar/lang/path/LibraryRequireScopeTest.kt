package net.internetisalie.lunar.lang.path

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

/**
 * BUG-62: project code and library code resolve under different rules. A library file, whatever
 * Lunar provider adds it (installed rocks, definition libraries, stubs, search path), matches a
 * module only at its relative path; a project file keeps the lenient bare-name match.
 */
class LibraryRequireScopeTest : BasePlatformTestCase() {
    private class FakeLibraryProvider(
        private val root: VirtualFile,
    ) : AdditionalLibraryRootsProvider() {
        override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> =
            listOf(SyntheticLibrary.newImmutableLibrary(listOf(root)))
    }

    fun testALibraryFileMatchesOnlyAtItsRelativePath() {
        val root = Files.createTempDirectory("lunar_bug62_lib")
        Files.createDirectories(root.resolve("luassert"))
        Files.writeString(root.resolve("luassert/util.lua"), "return {}")
        val rootFile = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root))
        rootFile.refresh(false, true)
        AdditionalLibraryRootsProvider.EP_NAME.point.registerExtension(
            FakeLibraryProvider(rootFile),
            testRootDisposable,
        )
        rescan()

        assertEquals(listOf<String>(), candidates("util").filter { it.endsWith("luassert/util.lua") })
        assertTrue(candidates("luassert.util").any { it.endsWith("luassert/util.lua") })
    }

    fun testAProjectFileKeepsTheBareNameMatch() {
        myFixture.addFileToProject("helpers/util.lua", "return {}")

        assertTrue(candidates("util").any { it.endsWith("helpers/util.lua") })
    }

    private fun rescan() {
        WriteAction.runAndWait<RuntimeException> {
            ProjectRootManagerEx
                .getInstanceEx(project)
                .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    private fun candidates(moduleName: String): List<String> =
        ReadAction.compute<List<String>, RuntimeException> {
            resolveModuleCandidates(project, moduleName).map { it.virtualFile.path }.toList()
        }
}
