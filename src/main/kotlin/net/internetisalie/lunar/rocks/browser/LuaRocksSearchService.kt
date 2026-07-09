package net.internetisalie.lunar.rocks.browser

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.LuaRocksEnvironment
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService

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
     * Returns an empty list if [query] is blank, or if the CLI is unavailable / offline.
     *
     * @param project used to resolve the effective executable and registry server (ROCKS-06).
     *   Pass `null` to use the application defaults.
     */
    fun search(query: String, project: Project? = null): List<LuaRockPackage> {
        if (query.isBlank()) return emptyList()
        val cached = LuaRocksSearchCache.get(query, System.currentTimeMillis())
        if (cached != null) return cached

        val exe = LuaRocksEnvironment.resolveExecutable(project) ?: run {
            log.warn("luarocks search skipped: no luarocks binary resolved")
            return emptyList()
        }
        val server = LuaRocksEnvironment.resolveServer(project)
        val subArgs = LuaRocksEnvironment.withServer(listOf("search", "--porcelain", query), server)
        val output = LuaToolExecutionService.getInstance().capture(
            GeneralCommandLine(exe, *subArgs.toTypedArray()),
            LuaExecTimeout.COMMAND,
        )
        if (output.exitCode != 0) {
            log.warn("luarocks search --porcelain $query exited ${output.exitCode}: ${output.stderr.trim()}")
            return emptyList()
        }

        val installedNames = installed(project)
        val packages = parseSearchOutput(output.stdout, installedNames)
        LuaRocksSearchCache.put(query, packages, System.currentTimeMillis())
        return packages
    }

    /**
     * Returns the set of installed package names (lower-case keys).
     * Uses `luarocks list --porcelain` (same field format as search).
     *
     * @param project used to resolve the effective executable and registry server (ROCKS-06).
     *   Pass `null` to use the application defaults.
     */
    fun installed(project: Project? = null): Set<String> {
        val exe = LuaRocksEnvironment.resolveExecutable(project) ?: run {
            log.warn("luarocks list skipped: no luarocks binary resolved")
            return emptySet()
        }
        val server = LuaRocksEnvironment.resolveServer(project)
        val subArgs = LuaRocksEnvironment.withServer(listOf("list", "--porcelain"), server)
        val output = LuaToolExecutionService.getInstance().capture(
            GeneralCommandLine(exe, *subArgs.toTypedArray()),
            LuaExecTimeout.COMMAND,
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
