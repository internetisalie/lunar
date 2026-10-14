package net.internetisalie.lunar.corpus

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.Compressor
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection
import net.internetisalie.lunar.definitions.LuaDefinitionCatalogLoader
import net.internetisalie.lunar.definitions.LuaDefinitionEntry
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryFetcher
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.io.File
import java.nio.file.Path

/**
 * MAINT-37 TC 8–13, 36 and 37: a pinned library staged from a local archive and enabled through
 * `LuaDefinitionLibraryEnabler` suppresses undeclared-variable reports, is released without a
 * trace, and both refresh paths observe the same refresh (design §2.8).
 *
 * No name here contains the sweep filter's word (design §2.16), so every case runs in the routine
 * suite.
 */
class DefinitionPinProvisioningTest : BasePlatformTestCase() {
    private var provisioned: List<Path> = emptyList()
    private lateinit var repoRoot: File

    override fun setUp() {
        super.setUp()
        CorpusDefinitionLibraries.requireSettingsSubscriber(project)
        repoRoot = FileUtil.createTempDirectory("maint37-pins", null)
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

    fun testProvisionedLibrarySuppressesUndeclaredGlobals() {
        writeFixtureArchives()
        provisioned = CorpusDefinitionLibraries.provision(project, bustedEntry(), repoRoot)

        val undeclared = highlightDescriptions().filter { it.startsWith("Undeclared variable '") }
        assertEmpty("a provisioned busted must declare describe and it", undeclared)
    }

    fun testWithoutProvisionDescribeAndItAreUndeclared() {
        writeFixtureArchives()

        val descriptions = highlightDescriptions()
        assertContainsElements(descriptions, "Undeclared variable 'describe'", "Undeclared variable 'it'")
    }

    fun testStaleCachedTreeIsReplacedFromTheArchive() {
        writeFixtureArchives()
        val busted = catalogEntry("busted")
        val staleTree =
            LuaDefinitionLibraryFetcher
                .defaultCacheRoot()
                .resolve(
                    "${busted.id}-${busted.version}",
                ).toFile()
        File(staleTree, "busted.lua").apply { parentFile.mkdirs() }.writeText("---@meta\n")
        provisioned = CorpusDefinitionLibraries.provision(project, bustedEntry(), repoRoot)

        assertDoesntContain(highlightDescriptions(), "Undeclared variable 'describe'")
    }

    fun testMissingArchiveNamesTheFetchScript() {
        val failure =
            runCatching {
                CorpusDefinitionLibraries.provision(
                    project,
                    bustedEntry(),
                    repoRoot,
                )
            }.exceptionOrNull()

        assertTrue(
            "a missing archive must throw IllegalStateException; was: $failure",
            failure is IllegalStateException,
        )
        val message = failure?.message.orEmpty()
        val archiveName = "busted-${catalogEntry("busted").version}.tar.gz"
        assertTrue("the error must name the remedy; was: $message", message.contains("tooling/corpus/fetch-corpus.py"))
        assertTrue("the error must name the archive; was: $message", message.contains(archiveName))
    }

    fun testReleaseEmptiesTheEnableListAndDeletesTrees() {
        writeFixtureArchives()
        provisioned = CorpusDefinitionLibraries.provision(project, bustedEntry(), repoRoot)
        val released = provisioned
        CorpusDefinitionLibraries.release(project, released)
        provisioned = emptyList()

        assertEmpty(LuaProjectSettings.getInstance(project).enabledDefinitionLibraries)
        assertEmpty("released trees must be gone from disk", released.filter { it.toFile().exists() })
    }

    fun testEnabledPinsIncludeDependencies() {
        writeFixtureArchives()
        provisioned = CorpusDefinitionLibraries.provision(project, bustedEntry(), repoRoot)

        val expected = listOf("busted", "luassert").associateWith { catalogEntry(it).version }
        assertEquals(expected, CorpusDefinitionLibraries.enabledPins(project))
    }

    fun testEnableAndReleaseWindowsEachObserveOneRefresh() {
        writeFixtureArchives()
        val entry = bustedEntry()
        val staged = CorpusDefinitionLibraries.stage(project, entry, repoRoot)
        provisioned = staged
        val enabled =
            CorpusDefinitionLibraries.observeRefresh(project, testRootDisposable) {
                CorpusDefinitionLibraries.enable(project, entry, repoRoot, staged)
            }
        val released =
            CorpusDefinitionLibraries.observeRefresh(project, testRootDisposable) {
                CorpusDefinitionLibraries.release(project, enabled.cacheDirs)
                emptyList()
            }
        provisioned = emptyList()

        assertEquals("enable window", ONE_REFRESH, enabled.observation)
        assertEquals("enable returns the staged trees", staged, enabled.cacheDirs)
        assertEquals("release window", ONE_REFRESH, released.observation)
    }

    fun testUnpinnedRefreshObservesTheEnableWindowTuple() {
        val observed =
            CorpusDefinitionLibraries.observeRefresh(project, testRootDisposable) {
                CorpusDefinitionLibraries.refreshUnpinned(project)
                emptyList()
            }

        assertEquals("unpinned refresh window", ONE_REFRESH, observed.observation)
        assertEmpty(observed.cacheDirs)
        assertEquals(emptyMap<String, String>(), CorpusDefinitionLibraries.enabledPins(project))
    }

    private fun bustedEntry(): CorpusEntry =
        CorpusEntry(
            name = "pin-fixture",
            commit = "0",
            roots = emptyList(),
            luaLevel = LuaLanguageLevel.LUA54,
            moduleRoot = null,
            definitionLibraries = listOf("busted"),
        )

    private fun catalogEntry(id: String): LuaDefinitionEntry =
        checkNotNull(LuaDefinitionCatalogLoader.load().entry(id)) { "no catalog entry '$id'" }

    private fun writeFixtureArchives() {
        val busted = catalogEntry("busted")
        val luassert = catalogEntry("luassert")
        writeArchive(
            CorpusDefinitionLibraries.archiveFile(repoRoot, busted),
            mapOf(
                "${busted.rootPrefix}/busted.lua" to
                    "---@meta\nfunction describe(name, block) end\nfunction it(name, block) end\n",
            ),
        )
        writeArchive(
            CorpusDefinitionLibraries.archiveFile(repoRoot, luassert),
            mapOf("${luassert.rootPrefix}/luassert.lua" to "---@meta\n"),
        )
    }

    private fun writeArchive(
        target: File,
        files: Map<String, String>,
    ) {
        target.parentFile.mkdirs()
        Compressor.Tar(target.toPath(), Compressor.Tar.Compression.GZIP).use { tar ->
            files.forEach { (path, text) -> tar.addFile(path, text.toByteArray()) }
        }
    }

    private fun highlightDescriptions(): List<String> {
        myFixture.configureByText("x_spec.lua", "describe(\"a\", function() it(\"b\", function() end) end)\n")
        return myFixture.doHighlighting().mapNotNull { it.description }
    }

    private companion object {
        val ONE_REFRESH = RefreshObservation(1, 1L, 4)
    }
}
