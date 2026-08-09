package net.internetisalie.lunar.type

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.definitions.LuaDefinitionCatalogLoader
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryFetcher
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.io.File

/**
 * TYPE-11 de-risking fixture: installs a library the way a **user** gets one, not the way a test
 * finds convenient.
 *
 * `LibraryRootTestCase` (COMP-09 DR-20's base) registers an *anonymous* `AdditionalLibraryRootsProvider`
 * over a temp tree. That proves resolution reaches a `SyntheticLibrary` root, which is what it was
 * built for — but it is invisible to any provenance test, because provenance asks
 * "did **this plugin** provision this file?" and the answer for an anonymous test provider is no.
 * Measuring TYPE-11 on that fixture would validate the mechanism against a path no user hits, which
 * is precisely the mistake COMP-09's Phase 2 abort was traced to.
 *
 * So this seeds the **real** definition-library cache directory
 * ([LuaDefinitionLibraryFetcher.defaultCacheRoot], `<system>/lunar/definitions/<id>-<version>`) under
 * a **real catalog id**, and enables it in the **real** project settings. The root then reaches the
 * project through the EP-registered `LuaDefinitionLibraryProvider` — the production instance, with
 * the production default fetcher — with no test-only seam anywhere in the chain.
 *
 * The cache root is per **user**, not per project, and the test JVM shares one system path across
 * every test class, so [tearDown] removes the tree. A leaked tree would silently register a library
 * into unrelated suites.
 */
abstract class TypeElevenDefinitionLibraryTestCase : BasePlatformTestCase() {
    private val seededDirectories = mutableListOf<File>()

    override fun setUp() {
        super.setUp()
        LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf()
    }

    override fun tearDown() {
        try {
            LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf()
            seededDirectories.forEach { it.deleteRecursively() }
        } finally {
            super.tearDown()
        }
    }

    /**
     * Writes [files] into the cache directory the production fetcher would have extracted [id] into,
     * enables [id], announces the roots change and waits for indexing. Returns the library root.
     */
    protected fun installDefinitionLibrary(
        id: String,
        files: Map<String, String>,
    ): VirtualFile {
        val entry = checkNotNull(LuaDefinitionCatalogLoader.load().entry(id)) { "no catalog entry '$id'" }
        val cacheRoot = LuaDefinitionLibraryFetcher.defaultCacheRoot()
        VfsRootAccess.allowRootAccess(testRootDisposable, cacheRoot.toString())
        val directory = cacheRoot.resolve("${entry.id}-${entry.version}").toFile()
        directory.mkdirs()
        seededDirectories.add(directory)
        files.forEach { (name, text) ->
            File(directory, name).apply { parentFile?.mkdirs() }.writeText(text)
        }
        val virtual =
            checkNotNull(VfsUtil.findFileByIoFile(directory, true)) {
                "definition cache tree not visible to the VFS at ${directory.absolutePath}"
            }
        VfsUtil.markDirtyAndRefresh(false, true, true, virtual)
        LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf(id)
        announceRootsChange()
        return virtual
    }

    protected fun announceRootsChange() {
        runWriteAction {
            ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    /** DR-20's library text, verbatim in shape: one `---@class`, 3 400 fields, 200 methods. */
    protected fun bigLibrary(): String {
        val root = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(3400) { i -> root.append("---@type number\nwx.wxC_$i = nil\n\n") }
        repeat(200) { i -> root.append("---@return boolean\nfunction wx.f$i() end\n\n") }
        root.append("return wx\n")
        return root.toString()
    }
}
