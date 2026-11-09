package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import net.internetisalie.lunar.rocks.LuaRocksEnvironment
import net.internetisalie.lunar.rocks.RockspecBridge
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService

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

    /**
     * Fetches metadata for [name] (and optionally [version]).
     * Returns null on CLI failure, timeout, or when no `luarocks` binary resolves
     * (passive/background path — degrades silently, design §3.3).
     *
     * @param project used to resolve the effective executable (pass `null` for app defaults).
     */
    fun show(
        name: String,
        version: String? = null,
        project: Project? = null,
    ): LuaRockMetadata? {
        val subArgs =
            buildList {
                add("show")
                add("--porcelain")
                add(name)
                if (!version.isNullOrBlank()) add(version)
            }
        val command = LuaRocksEnvironment.command(project, subArgs) ?: return null
        val output =
            LuaToolExecutionService
                .getInstance()
                .capture(command, LuaExecTimeout.COMMAND)
        if (output.exitCode != 0) {
            log.info(
                "luarocks show --porcelain $name ${version ?: ""} exited ${output.exitCode}: ${output.stderr.trim()}",
            )
            return null
        }
        return parseShowOutput(output.stdout, name)
    }

    /**
     * Metadata for the detail pane: an installed rock's from `luarocks show`, otherwise the rocks
     * server's rockspec, read without running it (BUG-58), or evaluated in an empty environment when it computes its fields (BUG-68). Null when neither answers.
     */
    fun describe(
        name: String,
        version: String?,
        project: Project,
    ): LuaRockMetadata? = show(name, version, project) ?: fromServer(name, version, project)

    /** Builds `["download", "--rockspec", <name>, <version?>]`; a blank version means the latest. */
    internal fun rockspecDownloadArgs(
        name: String,
        version: String?,
    ): List<String> =
        buildList {
            add("download")
            add("--rockspec")
            add(name)
            if (!version.isNullOrBlank()) add(version)
        }

    private fun fromServer(
        name: String,
        version: String?,
        project: Project,
    ): LuaRockMetadata? {
        val text = downloadRockspec(project, rockspecDownloadArgs(name, version)) ?: return null
        return runReadActionBlocking { RockspecMetadataReader.read(project, text) } ?: evaluated(project, text)
    }

    /**
     * A rockspec that computes its fields, evaluated by the bridge in an empty environment, which
     * reaches no standard library (BUG-50); the literal reader above cannot follow it (BUG-68).
     */
    private fun evaluated(
        project: Project,
        text: String,
    ): LuaRockMetadata? {
        val file = FileUtil.createTempFile("lunar-server", ".rockspec", true)
        try {
            file.writeText(text)
            return RockspecBridge.evaluate(project, file.toPath())?.let { RockspecJsonMetadata.read(it) }
        } finally {
            FileUtil.delete(file)
        }
    }

    /** Runs `luarocks download --rockspec` in a fresh temporary directory and returns the file's text. */
    private fun downloadRockspec(
        project: Project,
        subArgs: List<String>,
    ): String? {
        val dir = FileUtil.createTempDirectory("lunar-rockspec", null, true)
        try {
            val command = LuaRocksEnvironment.command(project, subArgs)?.withWorkDirectory(dir) ?: return null
            val output = LuaToolExecutionService.getInstance().capture(command, LuaExecTimeout.NETWORK)
            if (output.exitCode != 0) {
                log.info("luarocks ${subArgs.joinToString(" ")} exited ${output.exitCode}: ${output.stderr.trim()}")
                return null
            }
            return dir.listFiles { file -> file.name.endsWith(".rockspec") }?.firstOrNull()?.readText()
        } finally {
            FileUtil.delete(dir)
        }
    }

    // ── Parsing helper ───────────────────────────────────────────────────────

    /**
     * Parses `luarocks show --porcelain` stdout into [LuaRockMetadata].
     * Lines without a tab are skipped; unknown keys are ignored.
     */
    internal fun parseShowOutput(
        stdout: String,
        fallbackName: String,
    ): LuaRockMetadata? {
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
