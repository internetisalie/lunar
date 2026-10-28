package net.internetisalie.lunar.definitions

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.Compressor
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection
import net.internetisalie.lunar.corpus.CorpusDefinitionLibraries
import net.internetisalie.lunar.corpus.CorpusEntry
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.io.File
import java.nio.file.Path

/**
 * TARGET-11 TC 3–6: the `neovim` catalog entry, provisioned from a synthetic archive laid out like
 * the pinned commit archive, registers `runtime/lua` and nothing else.
 *
 * The archive's member paths are literal upstream paths, never derived from `rootPrefix`: a fixture
 * built from the prefix would move with a mutated prefix and hide the mutation.
 */
class NeovimDefinitionLibraryTest : BasePlatformTestCase() {
    private var provisioned: List<Path> = emptyList()
    private lateinit var repoRoot: File

    override fun setUp() {
        super.setUp()
        CorpusDefinitionLibraries.requireSettingsSubscriber(project)
        repoRoot = FileUtil.createTempDirectory("target11-neovim", null)
        VfsRootAccess.allowRootAccess(
            testRootDisposable,
            LuaDefinitionLibraryFetcher.defaultCacheRoot().toString(),
            repoRoot.canonicalPath,
        )
        LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf()
        myFixture.enableInspections(LuaUndeclaredVariableInspection())
    }

    override fun tearDown() {
        try {
            CorpusDefinitionLibraries.release(project, provisioned)
            if (::repoRoot.isInitialized) repoRoot.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    /** TC 3. */
    fun testVimIsDeclaredOnceProvisioned() {
        provisionNeovim()

        assertDoesntContain(
            undeclared("vim.schedule(function() end)\nvim.list_extend({}, {})\n"),
            "Undeclared variable 'vim'",
        )
    }

    /** TC 4 — the control that keeps TC 3 from passing vacuously. */
    fun testVimIsUndeclaredWithoutTheLibrary() {
        writeArchive()

        assertContainsElements(undeclared("vim.schedule(function() end)\n"), "Undeclared variable 'vim'")
    }

    /** TC 5. */
    fun testVimCompletesItsDirectMembers() {
        provisionNeovim()
        myFixture.configureByText("consumer.lua", "vim.<caret>\n")
        myFixture.completeBasic()

        assertContainsElements(myFixture.lookupElementStrings.orEmpty(), "list_extend", "schedule")
    }

    /** TC 6. */
    fun testFilesOutsideRuntimeLuaAreNotRegistered() {
        provisionNeovim()

        assertContainsElements(undeclared("outside_root()\n"), "Undeclared variable 'outside_root'")
    }

    private fun neovim(): LuaDefinitionEntry = checkNotNull(LuaDefinitionCatalogLoader.load().entry("neovim"))

    private fun writeArchive() {
        val library = neovim()
        val wrapper = "neovim-${library.version}"
        val target = CorpusDefinitionLibraries.archiveFile(repoRoot, library)
        target.parentFile.mkdirs()
        Compressor.Tar(target.toPath(), Compressor.Tar.Compression.GZIP).use { tar ->
            tar.addFile(
                "$wrapper/runtime/lua/vim/_core/shared.lua",
                "_G.vim = _G.vim or {}\n\nfunction vim.list_extend(dst, src) end\n".toByteArray(),
            )
            tar.addFile(
                "$wrapper/runtime/lua/vim/_meta/builtin.lua",
                "---@meta\nfunction vim.schedule(fn) end\n".toByteArray(),
            )
            tar.addFile("$wrapper/runtime/plugin/outside.lua", "function outside_root() end\n".toByteArray())
        }
    }

    private fun provisionNeovim() {
        writeArchive()
        provisioned =
            CorpusDefinitionLibraries.provision(
                project,
                CorpusEntry("neovim-fixture", "0", emptyList(), LuaLanguageLevel.LUA51, null, listOf("neovim")),
                repoRoot,
            )
    }

    private fun undeclared(text: String): List<String> {
        myFixture.configureByText("consumer.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Undeclared variable '") }
    }
}
