package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.LuaRocksEnvironment
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService
import java.nio.file.Path

/**
 * Shells out to `luarocks search --porcelain` / `luarocks list --porcelain` and parses the results.
 *
 * Must be called from a background thread only; [LuaToolExecutionService.capture] is blocking.
 *
 * ## Porcelain search format (§4.1 of ROCKS-02 design)
 * ```
 * <name> <version> <arch> <repo> [<namespace>]
 * ```
 * Lines with fewer than 4 fields are skipped. Multiple arch rows for the same `(name,version)`
 * are collapsed into one entry in the returned list; the UI version picker reads distinct versions.
 *
 * ## Offline / timeout handling
 * A non-zero exit code (a timeout or launch failure surfaces as exit code -1) produces an empty
 * list so callers degrade gracefully. Cached results (if fresh) are served by the caller before
 * reaching here.
 *
 * ## Server resolution (ROCKS-06)
 * The effective registry is resolved via [LuaRocksEnvironment.resolveServer]: project override
 * beats app default; `null` means no `--server` is emitted (preserves pre-ROCKS-06 behavior).
 * `--server` is a luarocks global flag and is prepended before the subcommand.
 */
object LuaRocksSearchService {
    private val log = Logger.getInstance(LuaRocksSearchService::class.java)

    /**
     * Returns packages matching [query], annotated with [LuaRockPackage.isInstalled].
     * Returns an empty list only when [query] is blank.
     *
     * Throws [BrowserCliError] (design §3.5) when no binary resolves or the CLI exits non-zero, so
     * the browser can render an honest error state instead of a misleading empty result. Non-browser
     * callers should use [searchOrEmpty], which preserves the silent-empty contract.
     *
     * @param project resolves the effective executable and registry server (ROCKS-06).
     * @param treeRoot the canonical tree whose installed rocks flag the ✓ cross-ref (design §2.3).
     */
    fun search(query: String, project: Project? = null, treeRoot: Path? = null): List<LuaRockPackage> {
        if (query.isBlank()) return emptyList()
        val server = LuaRocksEnvironment.resolveServer(project)
        LuaRocksSearchCache.get(query, server, System.currentTimeMillis())?.let { return it }

        val command = LuaRocksEnvironment.command(project, listOf("search", "--porcelain", query))
            ?: throw BrowserCliError(BrowserCliError.LUAROCKS_NOT_CONFIGURED)
        val output = LuaToolExecutionService.getInstance().capture(command, LuaExecTimeout.COMMAND)
        if (output.exitCode != 0) {
            throw BrowserCliError(output.stderr.trim().ifEmpty { "luarocks search exited ${output.exitCode}" })
        }

        val installedNames = installed(project, treeRoot)
        val packages = parseSearchOutput(output.stdout, installedNames)
        LuaRocksSearchCache.put(query, server, packages, System.currentTimeMillis())
        return packages
    }

    /** [search] wrapper preserving the graceful silent-empty path for non-browser callers (design §6). */
    fun searchOrEmpty(query: String, project: Project? = null, treeRoot: Path? = null): List<LuaRockPackage> =
        try {
            search(query, project, treeRoot)
        } catch (failure: BrowserCliError) {
            log.warn("luarocks search '$query' failed: ${failure.message}")
            emptyList()
        }

    /**
     * Returns the set of installed package names reported by `luarocks list --porcelain`, scoped to
     * [treeRoot] via `--tree` when non-null (design §2.3). Throws [BrowserCliError] on unresolved
     * binary / non-zero exit; [installedOrEmpty] keeps the graceful path.
     *
     * @param project resolves the effective executable (ROCKS-06).
     */
    fun installed(project: Project? = null, treeRoot: Path? = null): Set<String> {
        val subArgs = buildList {
            add("list"); add("--porcelain")
            if (treeRoot != null) { add("--tree"); add(treeRoot.toString()) }
        }
        val command = LuaRocksEnvironment.command(project, subArgs)
            ?: throw BrowserCliError(BrowserCliError.LUAROCKS_NOT_CONFIGURED)
        val output = LuaToolExecutionService.getInstance().capture(command, LuaExecTimeout.COMMAND)
        if (output.exitCode != 0) {
            throw BrowserCliError(output.stderr.trim().ifEmpty { "luarocks list exited ${output.exitCode}" })
        }
        return parseInstalledOutput(output.stdout)
    }

    /** [installed] wrapper preserving the graceful silent-empty path (design §6). */
    fun installedOrEmpty(project: Project? = null, treeRoot: Path? = null): Set<String> =
        try {
            installed(project, treeRoot)
        } catch (failure: BrowserCliError) {
            log.warn("luarocks list failed: ${failure.message}")
            emptySet()
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
