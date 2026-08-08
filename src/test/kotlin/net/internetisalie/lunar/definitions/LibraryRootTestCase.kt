package net.internetisalie.lunar.definitions

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
 * A test case with a real `SyntheticLibrary` root, for anything whose value depends on **library**
 * content rather than project content.
 *
 * Exists because a light fixture's own project is entirely inside `GlobalSearchScope.projectScope`,
 * so a projectScope-vs-allScope defect is structurally invisible to an ordinary
 * `BasePlatformTestCase`. That blind spot let BUG-395 and BUG-398 each ship with green suites while
 * the running IDE completed nothing, and BUG-399 and BUG-394 were both hiding behind it. If a
 * feature is supposed to work against a bundled stdlib stub, a definition library or a LuaRocks
 * tree, it needs a test here rather than one that seeds a project file and hopes.
 *
 * The plumbing is not obvious and is the reason this is shared rather than copied — see
 * `Dr03SyntheticLibraryResolutionSpikeTest`, which established it: root access has to be allowed for
 * a path outside the project, the roots change has to be announced because the platform caches its
 * root set, and the rescan that announcement schedules has to finish before anything is indexed.
 */
abstract class LibraryRootTestCase : BasePlatformTestCase() {
    /**
     * The lookup strings offered at the caret in [text].
     *
     * A lookup with exactly one match is **auto-inserted** and `completeBasic` returns null, so
     * reading the offered elements alone reports the single-perfect-match case as "nothing was
     * offered" — indistinguishable from the failure most tests here exist to catch. The inserted
     * word is recovered from the document instead.
     */
    protected fun completionsFor(text: String): List<String> {
        myFixture.configureByText("consumer.lua", text)
        val elements = myFixture.completeBasic()
        if (elements != null) return elements.map { it.lookupString }
        return listOf(insertedWordAtCaret())
    }

    /**
     * The word a single perfect match was auto-inserted as: the identifier ending at the caret.
     *
     * It must be read that way rather than as the whole line, which is what BUG-431 was. The line
     * still holds the receiver — `wx.wxID_<caret>` completes to a line reading `wx.wxID_ANY` — and a
     * member's lookup string is never qualified, so a line-wide read reports a member that resolved
     * correctly as one that came back under the wrong name, and does so only for the members whose
     * prefix happens to be unambiguous.
     */
    private fun insertedWordAtCaret(): String {
        val document = myFixture.editor.document.text
        val caret = myFixture.editor.caretModel.offset
        val start = document.take(caret).indexOfLast { !it.isLetterOrDigit() && it != '_' } + 1
        return document.substring(start, caret)
    }

    private class LibraryRootProvider(
        private val root: VirtualFile,
    ) : AdditionalLibraryRootsProvider() {
        override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> =
            listOf(
                object : SyntheticLibrary() {
                    override fun getSourceRoots(): Collection<VirtualFile> = listOf(root)

                    override fun equals(other: Any?): Boolean = other === this

                    override fun hashCode(): Int = root.hashCode()
                },
            )

        override fun getRootsToWatch(project: Project): Collection<VirtualFile> = listOf(root)
    }

    /**
     * Writes [files] (relative path → content) into a temp tree, exposes it as a registered library
     * source root, and waits until it is indexed. Returns the root.
     */
    protected fun registerLibraryRoot(files: Map<String, String>): VirtualFile {
        val root = seedTree(files)
        AdditionalLibraryRootsProvider.EP_NAME.point.registerExtension(LibraryRootProvider(root), testRootDisposable)
        runWriteAction {
            ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        return root
    }

    private fun seedTree(files: Map<String, String>): VirtualFile {
        val dir = Files.createTempDirectory(TEMP_PREFIX).toFile()
        val library = File(dir, "library").apply { mkdirs() }
        files.forEach { (relativePath, content) ->
            val target = File(library, relativePath)
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
        VfsRootAccess.allowRootAccess(testRootDisposable, dir.absolutePath)
        val virtual =
            checkNotNull(VfsUtil.findFileByIoFile(library, true)) {
                "library tree not visible to the VFS at ${library.absolutePath}"
            }
        VfsUtil.markDirtyAndRefresh(false, true, true, virtual)
        return virtual
    }

    protected companion object {
        /** Also the marker a test asserts on to prove resolution landed *in the library*. */
        const val TEMP_PREFIX = "lunar-library-root"
    }
}
