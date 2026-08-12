package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.definitions.LuaDefinitionCatalogLoader
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryFetcher
import net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder
import net.internetisalie.lunar.lang.psi.types.LuaTypes
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.platform.target.Target
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
    private var entryTarget: Target? = null

    override fun setUp() {
        super.setUp()
        entryTarget = LuaProjectSettings.getInstance(project).state.getTarget()
        LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf()
    }

    /**
     * The light project — and with it [LuaProjectSettings] — is **shared across test classes in one
     * JVM**, so a class that switches the target hands the next class a different bundled stdlib.
     * Measured: `TypeElevenGenerationSignalTest`'s TC-3 left the target on 5.1 and
     * `TypeElevenPinnableCostTest` then enumerated 9 stubs instead of 10.
     */
    override fun tearDown() {
        try {
            val settingsState = LuaProjectSettings.getInstance(project).state
            entryTarget?.takeIf { it != settingsState.getTarget() }?.let { settingsState.setTarget(it) }
            settingsState.enabledDefinitionLibraries = mutableListOf()
            seededDirectories.forEach { it.deleteRecursively() }
        } finally {
            super.tearDown()
        }
    }

    /**
     * The PSI file behind [virtualFile], read under a read action and never retained by the fixture
     * (engineering contract §4).
     */
    protected fun psiFileOf(virtualFile: VirtualFile): PsiFile =
        runReadAction {
            checkNotNull(PsiManager.getInstance(project).findFile(virtualFile)) { "no PSI for ${virtualFile.name}" }
        }

    /** [LuaTypesSnapshot.forFile] for [virtualFile], under a read action. */
    protected fun snapshotOf(virtualFile: VirtualFile): LuaTypes =
        runReadAction { LuaTypesSnapshot.forFile(psiFileOf(virtualFile)) }

    /**
     * The frame [LuaTypesSnapshot.forFile] registered for [types] (design §3.7 step 1).
     *
     * A missing entry is a fixture failure, not an assertion failure: every computed snapshot
     * registers one, so its absence means the snapshot under test was never built here.
     */
    protected fun frameOf(types: LuaTypes): LuaTypeSourceRecorder.SourceFrame =
        checkNotNull(LuaTypeSourceRecorder.snapshotFrames[types]) { "no recorded frame for this snapshot" }

    /**
     * Writes [files] into the cache directory the production fetcher would have extracted [id] into,
     * enables [id], announces the roots change and waits for indexing. Returns the library root.
     */
    protected fun installDefinitionLibrary(
        id: String,
        files: Map<String, String>,
    ): VirtualFile {
        val virtual = seedDefinitionLibrary(id, files)
        LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf(id)
        announceRootsChange()
        return virtual
    }

    /**
     * The disk half of [installDefinitionLibrary] with **no** enable and **no** roots change.
     *
     * TC-2b needs a tree that is on disk but not yet enabled, so that
     * `LuaDefinitionLibraryEnabler.apply` publishes for a list that genuinely differs from the
     * stored one while `missingEntries` stays empty and no network fetch is attempted.
     */
    protected fun seedDefinitionLibrary(
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
        return virtual
    }

    protected fun announceRootsChange() {
        runWriteAction {
            ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    /**
     * Edits [file] and **refuses to let the edit be healed by anything but itself**.
     *
     * Measured 2026-08-09 (DR-01): run alone, residual path 2 reports `[afterEdit, beforeEdit]`; run
     * after the other TYPE-11 classes in the same JVM it reports `[afterEdit]`, because a
     * `ProjectRootModificationTracker` tick left over from a previous class's library install
     * discards the pinned snapshot and hands the test a green it did not earn. A harness that can be
     * silently healed by an unrelated tick is not a gate, so the tick count is asserted to be still.
     */
    protected fun rewriteAssertingRootsAreStill(
        file: PsiFile,
        text: String,
    ) {
        val rootsTracker = ProjectRootModificationTracker.getInstance(project)
        val before = rootsTracker.modificationCount
        WriteCommandAction.runWriteCommandAction(project) {
            val document = checkNotNull(PsiDocumentManager.getInstance(project).getDocument(file))
            document.setText(text)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        assertEquals(
            "the roots tracker moved across the edit ($before -> ${rootsTracker.modificationCount}); " +
                "any verdict from this run would be about that tick, not about the edit",
            before,
            rootsTracker.modificationCount,
        )
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
