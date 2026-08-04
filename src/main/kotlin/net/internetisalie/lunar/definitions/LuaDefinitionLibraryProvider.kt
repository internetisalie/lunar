package net.internetisalie.lunar.definitions

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.provision.LuaProvisionException
import javax.swing.Icon

/**
 * Exposes each enabled **and fetched** definition library as a `SyntheticLibrary` source root, so
 * the platform indexes its `@meta` definitions (TARGET-08-04).
 *
 * Only ids whose cache directory actually exists contribute a root. That is what makes
 * TARGET-08-07 work without extra bookkeeping: a library the user enabled but whose fetch failed
 * stays in the persisted list — so a later online retry picks it up — while contributing nothing
 * here, rather than registering a root over a missing or half-extracted directory.
 *
 * Dependencies are resolved transitively: enabling `busted` registers `luassert` too, because
 * busted's own definitions open with `assert = require("luassert")`.
 *
 * Reads settings and VFS only — no PSI, no write actions. The platform calls this on both the EDT
 * and background threads, so it must stay cheap and side-effect free.
 */
class LuaDefinitionLibraryProvider(
    // Kotlin already synthesizes the no-arg constructor the extension point instantiates; the
    // parameter exists so tests can point the fetcher at a temp cache root.
    private val fetcher: LuaDefinitionLibraryFetcher = LuaDefinitionLibraryFetcher(),
) : AdditionalLibraryRootsProvider() {

    private val catalogFailureLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val roots = enabledRoots(project)
        if (roots.isEmpty()) return emptyList()
        return listOf(DefinitionLibrary(roots))
    }

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> = enabledRoots(project)

    private fun enabledRoots(project: Project): List<VirtualFile> {
        val enabled = LuaProjectSettings.getInstance(project).enabledDefinitionLibraries
        if (enabled.isEmpty()) return emptyList()
        val catalog = catalogOrNull() ?: return emptyList()
        // Emptiness is decided from the VFS, never `fetcher.isCached`. This runs on the EDT — the
        // platform calls it from cell rendering and from Goto File on every keystroke — and
        // `isCached` does a real `listDirectoryEntries()` opendir per enabled library per call,
        // allocating ~100 Paths for love2d and discarding them. `SyntheticLibrary`'s own javadoc
        // says implementations must be cheap here.
        return catalog.withDependencies(enabled).mapNotNull { fetcher.cachedRoot(it) }
    }

    /**
     * A corrupt bundled catalog must not take the project's other roots down with it — a provider
     * that throws breaks unrelated resolution. Logged once rather than silently swallowed: this is
     * called constantly, so logging every time would flood the log, and never logging would hide a
     * broken plugin build forever.
     */
    private fun catalogOrNull(): LuaDefinitionCatalog? =
        try {
            LuaDefinitionCatalogLoader.load()
        } catch (corrupt: LuaProvisionException) {
            if (catalogFailureLogged.compareAndSet(false, true)) {
                LOG.error("Bundled definition catalog is unreadable; no definition roots will load.", corrupt)
            }
            null
        }

    /**
     * One library node covering every enabled tree, mirroring `LuaRocksLibraryProvider`'s shape.
     *
     * [equals]/[hashCode] are by roots, not identity: the platform compares the collections it gets
     * across root changes to decide whether anything moved, so an identity-based library would
     * force a rescan on every call.
     */
    class DefinitionLibrary(private val roots: List<VirtualFile>) : SyntheticLibrary(), ItemPresentation {
        override fun getSourceRoots(): Collection<VirtualFile> = roots
        override fun getPresentableText(): String = "Lua Definition Libraries"
        override fun getIcon(unused: Boolean): Icon = LuaIcons.FILE
        // Compared as a SET: the platform diffs the collections it gets back to decide whether
        // roots moved, and ordering here follows catalog/dependency order, which is not a
        // meaningful difference. Order-sensitive equality would force spurious rescans.
        override fun hashCode(): Int = roots.toSet().hashCode()
        override fun equals(other: Any?): Boolean =
            other is DefinitionLibrary && other.roots.toSet() == roots.toSet()
    }

    private companion object {
        private val LOG = Logger.getInstance(LuaDefinitionLibraryProvider::class.java)
    }
}
