package net.internetisalie.lunar.definitions

import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * TARGET-08-04 / TC 5, 7b. Uses the real bundled catalog and a pre-seeded cache, so the ids and
 * `rootPrefix` under test are the ones that ship.
 *
 * Resolution *through* a registered root is proven separately by
 * [Dr03SyntheticLibraryResolutionSpikeTest] — that spike captures the fixture plumbing and the
 * finding that completion, unlike resolution, still cannot see library symbols.
 */
class LuaDefinitionLibraryProviderTest : BasePlatformTestCase() {

    private lateinit var cacheRoot: Path

    private val settings get() = LuaProjectSettings.getInstance(project)

    override fun setUp() {
        super.setUp()
        cacheRoot = Files.createTempDirectory("lunar-defs-provider")
        VfsRootAccess.allowRootAccess(testRootDisposable, cacheRoot.toString())
        // The light project is shared across test classes; see LuaDefinitionEnableListTest.
        settings.state.enabledDefinitionLibraries = mutableListOf()
    }

    override fun tearDown() {
        try {
            settings.state.enabledDefinitionLibraries = mutableListOf()
        } finally {
            super.tearDown()
        }
    }

    /** Writes a cache dir for [id] exactly where the fetcher would put it. */
    private fun seedCache(id: String) {
        val entry = checkNotNull(LuaDefinitionCatalogLoader.load().entry(id)) { "no catalog entry '$id'" }
        val dir = File(cacheRoot.toFile(), "${entry.id}-${entry.version}")
        dir.mkdirs()
        File(dir, "$id.lua").writeText("---@meta\nfunction ${id}_probe() end\n")
        VfsUtil.markDirtyAndRefresh(false, true, true, VfsUtil.findFileByIoFile(cacheRoot.toFile(), true))
    }

    private fun provider() = LuaDefinitionLibraryProvider(LuaDefinitionLibraryFetcher(cacheRoot))

    /** TC 7b — nothing enabled contributes nothing. */
    fun testNoEnabledLibrariesYieldsNoRoots() {
        assertEmpty(provider().getAdditionalProjectLibraries(project))
        assertEmpty(provider().getRootsToWatch(project))
    }

    /**
     * TARGET-08-07: an id enabled but never fetched must contribute no root. It stays in the
     * persisted list so a later online retry works — the provider simply skips it.
     */
    fun testEnabledButUncachedContributesNoRoot() {
        settings.state.enabledDefinitionLibraries = mutableListOf("busted")
        assertEmpty(provider().getAdditionalProjectLibraries(project))
    }

    /** TC 5 — an enabled, fetched library contributes exactly one library over its cache dir. */
    fun testEnabledAndCachedContributesItsRoot() {
        seedCache("luassert")
        settings.state.enabledDefinitionLibraries = mutableListOf("luassert")

        val libraries = provider().getAdditionalProjectLibraries(project)
        assertEquals(1, libraries.size)
        val roots = (libraries.first() as LuaDefinitionLibraryProvider.DefinitionLibrary).sourceRoots
        assertEquals(1, roots.size)
        assertTrue(roots.first().path.contains("luassert-"))
        assertEquals(roots.toList(), provider().getRootsToWatch(project).toList())
    }

    /**
     * Enabling busted alone must also register luassert: busted's definitions open with
     * `assert = require("luassert")`, so without the dependency it half-resolves.
     */
    fun testDependencyIsRegisteredTransitively() {
        seedCache("busted")
        seedCache("luassert")
        settings.state.enabledDefinitionLibraries = mutableListOf("busted")

        val roots = provider().getAdditionalProjectLibraries(project)
            .flatMap { (it as LuaDefinitionLibraryProvider.DefinitionLibrary).sourceRoots }
            .map { it.name }
        assertEquals(2, roots.size)
        assertTrue("expected the busted tree, got $roots", roots.any { it.startsWith("busted-") })
        assertTrue("expected luassert pulled in transitively, got $roots", roots.any { it.startsWith("luassert-") })
    }

    /** An empty cache directory is not a fetched library — it would index half a tree. */
    fun testEmptyCacheDirContributesNoRoot() {
        val entry = checkNotNull(LuaDefinitionCatalogLoader.load().entry("luassert"))
        File(cacheRoot.toFile(), "${entry.id}-${entry.version}").mkdirs()
        settings.state.enabledDefinitionLibraries = mutableListOf("luassert")
        assertEmpty(provider().getAdditionalProjectLibraries(project))
    }

    /** The provider is registered in plugin.xml, not merely instantiable — TC 5's real precondition. */
    fun testProviderIsRegisteredWithThePlatform() {
        val registered = AdditionalLibraryRootsProvider.EP_NAME.extensionList
            .any { it is LuaDefinitionLibraryProvider }
        assertTrue(
            "LuaDefinitionLibraryProvider must be registered in plugin.xml; a unit test that only " +
                "constructs it would pass while the feature is dead in a running IDE",
            registered,
        )
    }
}
