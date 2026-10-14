package net.internetisalie.lunar.corpus

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.nio.file.Files
import kotlin.io.path.writeText

/**
 * MAINT-37 TC 33–35: a member's `sourcePath` is restored after it, and a leaked out-of-project
 * module root stops being an indexed "Search Trees" library once restored (MAINT-37-09, BUG-496;
 * design §2.14).
 *
 * No name here contains the sweep filter's word (design §2.16), so every case runs in the routine
 * suite.
 */
class ModuleRootIsolationTest : BasePlatformTestCase() {
    private var saved: String = ""

    private val state get() = LuaProjectSettings.getInstance(project).state

    override fun setUp() {
        super.setUp()
        saved = CorpusSourcePath.snapshot(project)
    }

    override fun tearDown() {
        try {
            CorpusSourcePath.restore(project, saved)
            CorpusDefinitionLibraries.refreshRoots(project)
        } finally {
            super.tearDown()
        }
    }

    fun testRestoreWritesBackAChangedSourcePath() {
        state.sourcePath = "/nonexistent/?.lua"

        assertTrue(CorpusSourcePath.restore(project, saved))
        assertEquals(saved, state.sourcePath)
    }

    fun testRestoreOfAnUnchangedSourcePathReportsNoChange() {
        assertFalse(CorpusSourcePath.restore(project, saved))
    }

    fun testRestoredSourcePathLeavesNoIndexedFileBehind() {
        val moduleRoot = Files.createTempDirectory("lunar-module-root")
        moduleRoot.resolve(PROBE_FILE).writeText("return {}\n")
        VfsRootAccess.allowRootAccess(testRootDisposable, moduleRoot.toString())
        VfsUtil.markDirtyAndRefresh(false, true, true, moduleRoot.toFile())
        val where = "project.basePath=${project.basePath} D=$moduleRoot"

        state.sourcePath = "$moduleRoot/?.lua"
        CorpusDefinitionLibraries.refreshRoots(project)
        assertFalse("control: the leaked module root is not indexed ($where)", indexedProbes().isEmpty())

        CorpusSourcePath.restore(project, saved)
        CorpusDefinitionLibraries.refreshRoots(project)
        assertTrue("after restore: the module root is still indexed ($where)", indexedProbes().isEmpty())
    }

    private fun indexedProbes() =
        runReadActionBlocking {
            FilenameIndex.getVirtualFilesByName(PROBE_FILE, GlobalSearchScope.allScope(project))
        }

    private companion object {
        const val PROBE_FILE = "leakprobe.lua"
    }
}
