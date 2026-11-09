package net.internetisalie.lunar.rocks.browser

import net.internetisalie.lunar.rocks.deps.LuaRocksVersion
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
    private var installedRequestId: Long = 0
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
        if (backend.resolveTree() == null) {
            post(BrowserState.NoTree)
            return
        }
        refreshInstalled()
    }

    /**
     * Marks the Marketplace row installed, with an update when the installed [version] is older than
     * the newest (BUG-65), and re-lists the Installed tab (BUG-66). A null version is the latest.
     */
    fun onInstallSucceeded(
        name: String,
        version: String? = null,
    ) {
        LuaRocksSearchCache.invalidateAll()
        updateRow(name) { row ->
            val outdated =
                version != null &&
                    LuaRocksUpdateDetector.hasUpdate(version, row.versions.firstOrNull() ?: row.pkg.version)
            row.copy(installed = true, hasUpdate = outdated)
        }
        refreshInstalled()
    }

    /** Marks the Marketplace row not installed and re-lists the Installed tab (BUG-66). */
    fun onRemoveSucceeded(name: String) {
        LuaRocksSearchCache.invalidateAll()
        updateRow(name) { it.copy(installed = false, hasUpdate = false) }
        refreshInstalled()
    }

    /** Every version of [name] the rocks server offers, newest first, delivered on the EDT (BUG-65). */
    fun availableVersions(
        name: String,
        onResult: (List<String>) -> Unit,
    ) {
        val treeRoot = backend.resolveTree()
        backend.runInBackground {
            val versions =
                runCatching { backend.search(name, treeRoot) }
                    .getOrDefault(emptyList())
                    .filter { it.name == name }
                    .map { it.version }
                    .distinct()
                    .sortedByDescending { LuaRocksVersion.parse(it) }
            backend.onEdt { onResult(versions) }
        }
    }

    /** Re-lists the Installed tab under its own request id, so it never cancels a Marketplace search. */
    private fun refreshInstalled() {
        val treeRoot = backend.resolveTree() ?: return
        val id = ++installedRequestId
        backend.runInBackground { fetchInstalled(treeRoot, id) }
    }

    private fun fetchSearch(
        query: String,
        treeRoot: Path?,
        id: Long,
    ) {
        val outcome = runCatching { buildRows(backend.search(query, treeRoot), installedVersions(treeRoot)) }
        backend.onEdt { publishSearch(outcome, id) }
    }

    /** Installed version by name, so a row knows whether its newest version is an update. */
    private fun installedVersions(treeRoot: Path?): Map<String, String> {
        val tree = treeRoot ?: return emptyMap()
        return runCatching { backend.listInstalled(tree) }.getOrDefault(emptyList()).associate { it.name to it.version }
    }

    private fun publishSearch(
        outcome: Result<MutableList<LuaRockRow>>,
        id: Long,
    ) {
        if (id != requestId) return
        outcome
            .onSuccess { rows ->
                marketplaceRows = rows
                listener.onState(BrowserState.Results(marketplaceRows.toList()))
            }.onFailure { listener.onState(BrowserState.Error(messageOf(it))) }
    }

    /**
     * One row per package, in search order: the newest version, every version newest first, and
     * `hasUpdate` when the installed version is older than the newest (§3.2, BUG-59).
     */
    private fun buildRows(
        packages: List<LuaRockPackage>,
        installed: Map<String, String>,
    ): MutableList<LuaRockRow> =
        packages.groupBy { it.name }.values.mapTo(mutableListOf()) { group ->
            val newestFirst = group.sortedByDescending { LuaRocksVersion.parse(it.version) }
            val newest = newestFirst.first()
            val hasUpdate =
                installed[newest.name]?.let { LuaRocksUpdateDetector.hasUpdate(it, newest.version) } ?: false
            LuaRockRow(newest, newest.isInstalled, hasUpdate, newestFirst.map { it.version })
        }

    private fun fetchPopular(id: Long) {
        val entries = backend.fetchPopular()
        val installed = installedNames()
        backend.onEdt { publishPopular(entries, installed, id) }
    }

    /** Names in the project tree, so a popular row offers Uninstall for a rock already there (BUG-58). */
    private fun installedNames(): Set<String> {
        val tree = backend.resolveTree() ?: return emptySet()
        return runCatching { backend.listInstalled(tree) }.getOrDefault(emptyList()).mapTo(mutableSetOf()) { it.name }
    }

    private fun publishPopular(
        entries: List<PopularEntry>,
        installed: Set<String>,
        id: Long,
    ) {
        if (id != requestId) return
        if (entries.isEmpty()) {
            listener.onState(BrowserState.Idle)
            return
        }
        marketplaceRows =
            entries.mapTo(mutableListOf()) { LuaRockRow(popularPackage(it), installed = it.name in installed) }
        listener.onState(BrowserState.Results(marketplaceRows.toList()))
    }

    /** The stats page names no version, so it stays blank and install takes the latest (BUG-56). */
    private fun popularPackage(entry: PopularEntry): LuaRockPackage = LuaRockPackage(entry.name, "", "popular", "", "")

    private fun fetchInstalled(
        treeRoot: Path,
        id: Long,
    ) {
        val outcome = runCatching { backend.listInstalled(treeRoot) }
        backend.onEdt { publishInstalled(outcome, id) }
    }

    private fun publishInstalled(
        outcome: Result<List<InstalledRockRow>>,
        id: Long,
    ) {
        if (id != installedRequestId) return
        outcome
            .onSuccess { listener.onState(BrowserState.Installed(it)) }
            .onFailure { listener.onState(BrowserState.Error(messageOf(it))) }
    }

    private fun updateRow(
        name: String,
        transform: (LuaRockRow) -> LuaRockRow,
    ) {
        val index = marketplaceRows.indexOfFirst { it.pkg.name == name }
        if (index < 0) return
        marketplaceRows[index] = transform(marketplaceRows[index])
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
