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

    /** Runs a Marketplace search; blank query → the neutral [BrowserState.Idle] prompt (design §3.3). */
    fun runMarketplaceSearch(query: String) {
        if (query.isBlank()) {
            post(BrowserState.Idle)
            return
        }
        val id = beginRequest()
        val treeRoot = backend.resolveTree()
        backend.runInBackground { fetchSearch(query, treeRoot, id) }
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
                marketplaceRows = packages.mapTo(mutableListOf()) { LuaRockRow(it, it.isInstalled) }
                listener.onState(BrowserState.Results(marketplaceRows.toList()))
            }
            .onFailure { listener.onState(BrowserState.Error(messageOf(it))) }
    }

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
