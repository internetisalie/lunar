package net.internetisalie.lunar.rocks.browser

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import net.internetisalie.lunar.rocks.run.LuaRocksSettings
import net.internetisalie.lunar.util.LuaProcessUtil

/**
 * Shells out to `luarocks search --porcelain` / `luarocks list --porcelain` and parses the results.
 *
 * Must be called from a background thread only; [LuaProcessUtil.capture] is blocking.
 *
 * ## Porcelain search format (§4.1 of ROCKS-02 design)
 * ```
 * <name> <version> <arch> <repo> [<namespace>]
 * ```
 * Lines with fewer than 4 fields are skipped. Multiple arch rows for the same `(name,version)`
 * are collapsed into one entry in the returned list; the UI version picker reads distinct versions.
 *
 * ## Offline / timeout handling
 * A non-zero exit code or a timeout (exit code [LuaProcessUtil.PROCESS_TIMEOUT_EXCEPTION_CODE])
 * produces an empty list so callers degrade gracefully. Cached results (if fresh) are served by
 * the caller before reaching here.
 */
object LuaRocksSearchService {
    private val log = Logger.getInstance(LuaRocksSearchService::class.java)

    private const val SEARCH_TIMEOUT_MS = 15_000

    /**
     * Returns packages matching [query], annotated with [LuaRockPackage.isInstalled].
     * Returns an empty list if [query] is blank, or if the CLI is unavailable / offline.
     */
    fun search(query: String): List<LuaRockPackage> {
        if (query.isBlank()) return emptyList()
        val cached = LuaRocksSearchCache.get(query, System.currentTimeMillis())
        if (cached != null) return cached

        val exe = LuaRocksSettings.getInstance().executablePath
        val output = LuaProcessUtil.capture(
            GeneralCommandLine(exe, "search", "--porcelain", query),
            SEARCH_TIMEOUT_MS,
        )
        if (output.exitCode != 0) {
            log.warn("luarocks search --porcelain $query exited ${output.exitCode}: ${output.stderr.trim()}")
            return emptyList()
        }

        val installedNames = installed()
        val packages = parseSearchOutput(output.stdout, installedNames)
        LuaRocksSearchCache.put(query, packages, System.currentTimeMillis())
        return packages
    }

    /**
     * Returns the set of installed package names (lower-case keys).
     * Uses `luarocks list --porcelain` (same field format as search).
     */
    fun installed(): Set<String> {
        val exe = LuaRocksSettings.getInstance().executablePath
        val output = LuaProcessUtil.capture(
            GeneralCommandLine(exe, "list", "--porcelain"),
            SEARCH_TIMEOUT_MS,
        )
        if (output.exitCode != 0) {
            log.warn("luarocks list --porcelain exited ${output.exitCode}: ${output.stderr.trim()}")
            return emptySet()
        }
        return parseInstalledOutput(output.stdout)
    }

    // ── Parsing helpers ─────────────────────────────────────────────────────

    /**
     * Parses `luarocks search --porcelain` stdout and collapses arch variants.
     *
     * Each line: `<name> <version> <arch> <repo> [<namespace>]`
     * Lines with < 4 fields are silently skipped.
     * Rows sharing the same `(name, version)` are merged into one [LuaRockPackage] (first repo/ns wins).
     */
    internal fun parseSearchOutput(stdout: String, installedNames: Set<String>): List<LuaRockPackage> {
        // Ordered map to preserve result order while collapsing arch variants
        val seen = LinkedHashMap<Pair<String, String>, LuaRockPackage>()
        for (line in stdout.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val f = trimmed.split(Regex("\\s+"))
            if (f.size < 4) continue
            val key = f[0] to f[1]
            if (!seen.containsKey(key)) {
                seen[key] = LuaRockPackage(
                    name = f[0],
                    version = f[1],
                    arch = f[2],
                    repo = f[3],
                    namespace = f.getOrElse(4) { "" },
                    isInstalled = f[0] in installedNames,
                )
            }
        }
        return seen.values.toList()
    }

    /**
     * Parses `luarocks list --porcelain` stdout; returns the set of installed package names.
     * Blank lines are ignored; only the first field is read.
     */
    internal fun parseInstalledOutput(stdout: String): Set<String> {
        val names = mutableSetOf<String>()
        for (line in stdout.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val f = trimmed.split(Regex("\\s+"))
            if (f.isNotEmpty()) names += f[0]
        }
        return names
    }
}
