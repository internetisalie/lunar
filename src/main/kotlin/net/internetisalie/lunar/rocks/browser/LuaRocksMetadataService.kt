package net.internetisalie.lunar.rocks.browser

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import net.internetisalie.lunar.rocks.run.LuaRocksSettings
import net.internetisalie.lunar.util.LuaProcessUtil

/**
 * Fetches rich package metadata via `luarocks show --porcelain <name> [version]` (ROCKS-02-02).
 *
 * Must be called from a background thread.
 *
 * ## Porcelain show format (§4.2 of ROCKS-02 design)
 * Tab-separated `key\tvalue[\tvalue2]` lines. Keys include `package`, `version`, `summary`,
 * `detailed`, `license`, `homepage`, `issues`, `location`, `module`, `dependency`, etc.
 * Unknown keys are silently ignored; lines without a tab separator are skipped.
 *
 * Returns null if the CLI fails, times out, or the package is not found.
 */
object LuaRocksMetadataService {
    private val log = Logger.getInstance(LuaRocksMetadataService::class.java)

    private const val SHOW_TIMEOUT_MS = 15_000

    /**
     * Fetches metadata for [name] (and optionally [version]).
     * Returns null on CLI failure or timeout.
     */
    fun show(name: String, version: String? = null): LuaRockMetadata? {
        val exe = LuaRocksSettings.getInstance().executablePath
        val args = buildList {
            add(exe); add("show"); add("--porcelain"); add(name)
            if (version != null) add(version)
        }
        val output = LuaProcessUtil.capture(GeneralCommandLine(args), SHOW_TIMEOUT_MS)
        if (output.exitCode != 0) {
            log.warn("luarocks show --porcelain $name ${version ?: ""} exited ${output.exitCode}: ${output.stderr.trim()}")
            return null
        }
        return parseShowOutput(output.stdout, name)
    }

    // ── Parsing helper ───────────────────────────────────────────────────────

    /**
     * Parses `luarocks show --porcelain` stdout into [LuaRockMetadata].
     * Lines without a tab are skipped; unknown keys are ignored.
     */
    internal fun parseShowOutput(stdout: String, fallbackName: String): LuaRockMetadata? {
        var name: String? = null
        var version: String? = null
        var summary: String? = null
        val detailedParts = mutableListOf<String>()
        var license: String? = null
        var homepage: String? = null
        var issues: String? = null
        var location: String? = null
        val dependencies = mutableListOf<String>()
        val modules = mutableListOf<String>()

        for (line in stdout.lineSequence()) {
            val tabIdx = line.indexOf('\t')
            if (tabIdx < 0) continue
            val key = line.substring(0, tabIdx)
            val rest = line.substring(tabIdx + 1)
            when (key) {
                "package" -> name = rest.trim()
                "version" -> version = rest.trim()
                "summary" -> summary = rest.trim()
                "detailed" -> detailedParts += rest.trim()
                "license" -> license = rest.trim()
                "homepage" -> homepage = rest.trim()
                "issues" -> issues = rest.trim()
                "location" -> location = rest.trim()
                "module" -> {
                    // format: module\t<name>\t<file>
                    val modName = rest.substringBefore('\t').trim()
                    if (modName.isNotEmpty()) modules += modName
                }
                "dependency", "build_dependency", "test_dependency", "indirect_dependency" -> {
                    // format: dependency\t<name>\t<label>
                    val depName = rest.substringBefore('\t').trim()
                    val depLabel = rest.substringAfter('\t', "").trim()
                    val dep = if (depLabel.isEmpty()) depName else "$depName $depLabel"
                    if (dep.isNotEmpty()) dependencies += dep
                }
                // namespace, labels, command — silently ignored in v1
            }
        }
        // If output was completely empty or malformed, bail out
        val resolvedName = name ?: fallbackName.takeIf { it.isNotEmpty() } ?: return null
        val resolvedVersion = version ?: return null
        return LuaRockMetadata(
            name = resolvedName,
            version = resolvedVersion,
            summary = summary,
            detailed = detailedParts.joinToString("\n").ifEmpty { null },
            license = license,
            homepage = homepage,
            issues = issues,
            location = location,
            dependencies = dependencies,
            modules = modules,
        )
    }
}
