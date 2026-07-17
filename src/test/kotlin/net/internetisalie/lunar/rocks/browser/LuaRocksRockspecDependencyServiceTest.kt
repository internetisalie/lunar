package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * ROCKS-16-13 (DR-05): [LuaRocksRockspecDependencyService] appends the rock to a project rockspec's
 * `dependencies` under a write command (the plan's Phase-7 unit exit). The write is exercised
 * against a real fixture VFS file; the discovery→resolve wrapper's not-found path is asserted too.
 *
 * NOTE: the light-fixture `temp://` VFS does not round-trip a discovered nio `Path` back to a
 * `VirtualFile` (`VfsUtil.findFile`/`refreshAndFindFileByNioPath` are LocalFileSystem-oriented), so
 * the write is verified via the testable `applyTo(rockspec, …)` core — production `addDependency`
 * resolves a real local rockspec exactly this way. The pure edit logic is covered by
 * `RockspecDependencyEditorTest`.
 */
class LuaRocksRockspecDependencyServiceTest : BasePlatformTestCase() {

    fun `test applyTo writes the rock into the rockspec under a write command`() {
        val rockspec: VirtualFile = myFixture.addFileToProject(
            "demo-1.0-1.rockspec",
            "package = \"demo\"\nversion = \"1.0-1\"\ndependencies = { \"lua >= 5.1\" }\n",
        ).virtualFile

        LuaRocksRockspecDependencyService(project).applyTo(rockspec, "inspect", ">= 3.1")

        val text = VfsUtil.loadText(rockspec)
        assertTrue("dependency should be appended: $text", text.contains("\"inspect >= 3.1\""))
        assertTrue("original dependency preserved: $text", text.contains("\"lua >= 5.1\""))
    }

    fun `test addDependency returns false when no rockspec exists`() {
        assertFalse(LuaRocksRockspecDependencyService(project).addDependency("inspect", ">= 3.1"))
    }
}
