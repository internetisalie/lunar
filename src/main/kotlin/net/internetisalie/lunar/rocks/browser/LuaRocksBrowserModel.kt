package net.internetisalie.lunar.rocks.browser

import java.nio.file.Path

/**
 * Per-panel, EDT-confined state machine driving the LuaRocks browser (design §2.5 / §3.3 / §3.4).
 *
 * Owns the current [BrowserState], the Marketplace row list, and the Installed row list. CLI work is
 * kicked to a background thread via the [backend] and marshalled back with [LuaRocksBrowserBackend.onEdt];
 * every posted result is checked against a monotonically increasing [requestId] so a slow search never
 * overwrites a newer one (design §6, review findings #48/#71b). All mutation happens on the EDT.
 *
 * The [backend] seam lets model tests inject a synchronous fake with no real `luarocks` and no EDT.
 */
class LuaRocksBrowserModel(
    private val backend: LuaRocksBrowserBackend,
    private val listener: Listener,
) {
    interface Listener {
        fun onState(state: BrowserState)
        fun onRowChanged(index: Int)
    }

    private var requestId: Long = 0
    private var marketplaceRows: MutableList<LuaRockRow> = mutableListOf()

    /** The current Marketplace rows (read-only view for the panel/tests). */
    val currentRows: List<LuaRockRow> get() = marketplaceRows

    /** Runs a Marketplace search; blank query → the popular list, degrading to [BrowserState.Idle] (§3.3/§3.3a). */
    fun runMarketplaceSearch(query: String) {
        if (query.isBlank()) {
            loadPopular()
            return
        }
        val id = beginRequest()
        val treeRoot = backend.resolveTree()
        backend.runInBackground { fetchSearch(query, treeRoot, id) }
    }

    /**
     * Best-effort zero-query "Popular" list (ROCKS-16-15, §3.3a). A non-empty list → [BrowserState.Results];
     * an empty list → [BrowserState.Idle] (the neutral prompt). Never an error state.
     */
    fun loadPopular() {
        val id = beginRequest()
        backend.runInBackground { fetchPopular(id) }
    }

    /** Loads the Installed tab; no tree → [BrowserState.NoTree] (design §3.3 Installed / §4.1). */
    fun loadInstalled() {
        val treeRoot = backend.resolveTree() ?: run {
            post(BrowserState.NoTree)
            return
        }
        val id = beginRequest()
        backend.runInBackground { fetchInstalled(treeRoot, id) }
    }

    /** Flips the matching Marketplace row to installed in place and invalidates the cache (design §3.4). */
    fun onInstallSucceeded(name: String) {
        LuaRocksSearchCache.invalidateAll()
        flipRow(name, installed = true)
    }

    /** Flips the matching Marketplace row to not-installed in place and invalidates the cache. */
    fun onRemoveSucceeded(name: String) {
        LuaRocksSearchCache.invalidateAll()
        flipRow(name, installed = false)
    }

    private fun fetchSearch(query: String, treeRoot: Path?, id: Long) {
        val outcome = runCatching { backend.search(query, treeRoot) }
        backend.onEdt { publishSearch(outcome, id) }
    }

    private fun publishSearch(outcome: Result<List<LuaRockPackage>>, id: Long) {
        if (id != requestId) return
        outcome
            .onSuccess { packages ->
                marketplaceRows = buildRows(packages)
                listener.onState(BrowserState.Results(marketplaceRows.toList()))
            }
            .onFailure { listener.onState(BrowserState.Error(messageOf(it))) }
    }

    /** Builds rows, flagging `hasUpdate` on an installed rock whose latest search version is newer (§3.2). */
    private fun buildRows(packages: List<LuaRockPackage>): MutableList<LuaRockRow> {
        val latestByName = packages.groupBy { it.name }
            .mapValues { (_, group) -> group.map { LuaRockRow(it, false) } }
            .mapValues { (_, rows) -> LuaRocksUpdateDetector.latestOf(rows) }
        return packages.mapTo(mutableListOf()) { pkg ->
            val hasUpdate = pkg.isInstalled && LuaRocksUpdateDetector.hasUpdate(pkg.version, latestByName[pkg.name])
            LuaRockRow(pkg, pkg.isInstalled, hasUpdate)
        }
    }

    private fun fetchPopular(id: Long) {
        val entries = backend.fetchPopular()
        backend.onEdt { publishPopular(entries, id) }
    }

    private fun publishPopular(entries: List<PopularEntry>, id: Long) {
        if (id != requestId) return
        if (entries.isEmpty()) {
            listener.onState(BrowserState.Idle)
            return
        }
        marketplaceRows = entries.mapTo(mutableListOf()) { LuaRockRow(popularPackage(it), installed = false) }
        listener.onState(BrowserState.Results(marketplaceRows.toList()))
    }

    private fun popularPackage(entry: PopularEntry): LuaRockPackage =
        LuaRockPackage(entry.name, entry.count ?: "", "popular", "", "")

    private fun fetchInstalled(treeRoot: Path, id: Long) {
        val outcome = runCatching { backend.listInstalled(treeRoot) }
        backend.onEdt { publishInstalled(outcome, id) }
    }

    private fun publishInstalled(outcome: Result<List<InstalledRockRow>>, id: Long) {
        if (id != requestId) return
        outcome
            .onSuccess { listener.onState(BrowserState.Installed(it)) }
            .onFailure { listener.onState(BrowserState.Error(messageOf(it))) }
    }

    private fun flipRow(name: String, installed: Boolean) {
        val index = marketplaceRows.indexOfFirst { it.pkg.name == name }
        if (index < 0) return
        marketplaceRows[index] = marketplaceRows[index].copy(installed = installed)
        listener.onRowChanged(index)
    }

    private fun beginRequest(): Long {
        requestId += 1
        post(BrowserState.Loading)
        return requestId
    }

    private fun post(state: BrowserState) = listener.onState(state)

    private fun messageOf(failure: Throwable): String =
        (failure as? BrowserCliError)?.message ?: failure.message ?: "LuaRocks error"
}
