package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

/**
 * BUG-399: `require` must reach a module that lives in a definition-library root, and the
 * `---@class` it exports must materialize.
 *
 * Found re-running TARGET-08 TC 6 live: with `busted` enabled, `assert` *resolved* into the library
 * (Ctrl+B offered busted's `assert = require("luassert")`) but `assert.` completed nothing, because
 * the `require("luassert")` inside that library file resolved to nothing and the global's type
 * degraded to `Any`.
 *
 * Built on the TARGET-00-DR-03 harness — a real registered `SyntheticLibrary` root — because that is
 * the only setup where a project-scope-vs-all-scope defect can show itself at all. Everything in the
 * light fixture's own project is in project scope, which is exactly why the unit tests for BUG-395
 * and BUG-398 passed while the live IDE did not.
 */
class LuaLibraryModuleResolutionTest : BasePlatformTestCase() {

    private class LibraryRootProvider(private val root: VirtualFile) : AdditionalLibraryRootsProvider() {
        override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> =
            listOf(object : SyntheticLibrary() {
                override fun getSourceRoots(): Collection<VirtualFile> = listOf(root)
                override fun equals(other: Any?): Boolean = other === this
                override fun hashCode(): Int = root.hashCode()
            })

        override fun getRootsToWatch(project: Project): Collection<VirtualFile> = listOf(root)
    }

    /**
     * A library tree shaped like the real luassert: a `---@class` on a local, methods declared
     * against that local, and the local returned as the module.
     */
    private fun seedLibrary(): VirtualFile {
        val dir = Files.createTempDirectory("lunar-bug399").toFile()
        val library = File(dir, "library").apply { mkdirs() }
        File(library, "libmod.lua").writeText(
            """
            ---@meta

            ---@class libmod.internal
            local internal = {}
            ---@param value any
            function internal.inherited(value) end

            ---@class libmod : libmod.internal
            local libmod = {}
            ---@param name string
            function libmod.own(name) end
            return libmod
            """.trimIndent(),
        )
        VfsRootAccess.allowRootAccess(testRootDisposable, dir.absolutePath)
        val virtual = checkNotNull(VfsUtil.findFileByIoFile(library, true)) { "library tree not visible to the VFS" }
        VfsUtil.markDirtyAndRefresh(false, true, true, virtual)
        return virtual
    }

    private fun registerLibrary() {
        val root = seedLibrary()
        AdditionalLibraryRootsProvider.EP_NAME.point.registerExtension(LibraryRootProvider(root), testRootDisposable)
        // The platform caches its root set; the change has to be announced, and the rescan it
        // schedules has to finish, before anything in the tree is indexed (DR-03).
        runWriteAction {
            ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    /** The first broken link: `require` of a module that only exists in a library root. */
    fun testRequireResolvesIntoALibraryRoot() {
        registerLibrary()
        myFixture.configureByText("consumer.lua", "local m = require(\"libmod\")\n")
        val resolved = runReadAction {
            myFixture.file.findReferenceAt(myFixture.file.text.indexOf("libmod\"") + 1)
                ?.resolve()?.containingFile?.virtualFile?.path
        }
        assertNotNull("require must resolve into the definition-library root", resolved)
        assertTrue("expected resolution into the library tree, got $resolved", resolved!!.contains("lunar-bug399"))
    }

    /** The second: the `---@class` the module exports must materialize from a library file. */
    fun testMembersOfALibraryClassComplete() {
        registerLibrary()
        myFixture.configureByText("consumer.lua", "local m = require(\"libmod\")\nm.<caret>\n")
        myFixture.completeBasic()
        val found = myFixture.lookupElementStrings.orEmpty()
        assertTrue("the module's own member must complete. Found: $found", found.contains("own"))
        assertTrue("its inherited member must complete too. Found: $found", found.contains("inherited"))
    }

    /** And the whole TC 6 chain: a global bound to that module, consumed from a project file. */
    fun testGlobalBoundToALibraryModuleCompletesItsMembers() {
        registerLibrary()
        myFixture.addFileToProject("bootstrap.lua", "---@meta\nglobalmod = require(\"libmod\")\n")
        myFixture.configureByText("consumer.lua", "globalmod.<caret>\n")
        myFixture.completeBasic()
        val found = myFixture.lookupElementStrings.orEmpty()
        assertTrue("TC 6 shape: own member must complete. Found: $found", found.contains("own"))
        assertTrue("TC 6 shape: inherited member must complete. Found: $found", found.contains("inherited"))
    }
}
