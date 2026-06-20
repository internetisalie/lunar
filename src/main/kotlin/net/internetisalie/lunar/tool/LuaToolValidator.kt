package net.internetisalie.lunar.tool

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.logger
import net.internetisalie.lunar.util.LuaProcessUtil
import java.io.File
import java.util.regex.Pattern

/**
 * Validates an external Lua tool binary by invoking it with a version flag and parsing the output.
 *
 * Design contract (TOOL-01):
 * - Both `stdout` and `stderr` are merged before pattern matching (many tools print to `stderr`).
 * - A 10-second hard timeout is applied to every CLI call.
 * - All methods are synchronous and must be called from a background thread (never the EDT).
 */
object LuaToolValidator {

    private val LOG = logger<LuaToolValidator>()

    /** Timeout (ms) for each CLI invocation. */
    const val VERSION_TIMEOUT_MS: Int = 10_000

    // ---------------------------------------------------------------------------
    // Version patterns per tool type
    // ---------------------------------------------------------------------------

    /**
     * Matches: "LuaRocks 3.9.2"  /  "LuaRocks 3.11.0-1 ..."
     * Captures: group(1) = version string.
     */
    private val LUAROCKS_VERSION = Pattern.compile("""LuaRocks\s+(\S+)""", Pattern.CASE_INSENSITIVE)

    /**
     * Matches: "Luacheck: 0.26.0"  /  "luacheck 0.25.0"
     * Captures: group(1) = version string.
     */
    private val LUACHECK_VERSION = Pattern.compile("""[Ll]uacheck[:\s]+(\S+)""")

    /**
     * Matches: "stylua 0.20.0"
     * Captures: group(1) = version string.
     */
    private val STYLUA_VERSION = Pattern.compile("""stylua\s+(\S+)""", Pattern.CASE_INSENSITIVE)

    /**
     * Matches: "busted 2.2.0" or just "2.2.0"
     * Captures: group(1) = version string.
     */
    private val BUSTED_VERSION = Pattern.compile("""(?:busted\s+)?(\S+)""", Pattern.CASE_INSENSITIVE)

    /**
     * Matches: "LuaCov 0.15.0 - coverage analyzer for Lua"
     * Captures: group(1) = version string.
     */
    private val LUACOV_VERSION = Pattern.compile("""LuaCov\s+(\S+)""", Pattern.CASE_INSENSITIVE)

    /**
     * LuaRocks version flag to identify supported Lua interpreter.
     * E.g.: "... for Lua 5.4 ..."
     */
    private val LUAROCKS_LUA_VERSION = Pattern.compile("""for Lua\s+([\d.]+)""")

    /**
     * Minimum LuaRocks version required for ROCKS integration.
     * Semver-comparable component comparison (major.minor.patch).
     */
    private val MINIMUM_LUAROCKS_VERSION = SemanticVersion(3, 0, 0)

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Extracts the version string from the tool at [path].
     *
     * @return The version string (e.g. "3.9.2"), or `null` if the tool could not be run or
     *         the output did not match the expected pattern.
     */
    fun extractVersion(path: String, type: LuaToolType): String? {
        val output = runVersionCommand(path, type) ?: return null
        val pattern = patternFor(type)
        val matcher = pattern.matcher(output)
        if (!matcher.find()) {
            LOG.debug("[$type] version pattern did not match output from '$path': $output")
            return null
        }
        return matcher.group(1)
    }

    /**
     * Best-effort extraction of the Lua version that the tool is bound to.
     * Only meaningful for LuaRocks (which embeds "for Lua 5.x" in its version output).
     *
     * @return A version label like "5.4", or empty string if unavailable.
     */
    fun detectLuaVersion(path: String, type: LuaToolType): String {
        if (type != LuaToolType.LUAROCKS) return ""
        val output = runVersionCommand(path, type) ?: return ""
        val matcher = LUAROCKS_LUA_VERSION.matcher(output)
        return if (matcher.find()) matcher.group(1) else ""
    }

    /**
     * Returns `true` if the [tool]'s Lua version is compatible with [interpreterLuaVersion].
     *
     * Compatibility rule (TOOL-01-06): if either version is unknown (empty), we optimistically
     * report compatible. Otherwise the major.minor must match.
     */
    fun checkCompatibility(tool: LuaTool, interpreterLuaVersion: String): Boolean {
        if (tool.luaVersion.isEmpty() || interpreterLuaVersion.isEmpty()) return true
        return tool.luaVersion == interpreterLuaVersion
    }

    /**
     * Returns `true` if the LuaRocks tool at [path] meets [MINIMUM_LUAROCKS_VERSION].
     * Always returns `true` for non-LUAROCKS tools (requirement does not apply).
     */
    fun meetsMinimumVersion(path: String, type: LuaToolType): Boolean {
        if (type != LuaToolType.LUAROCKS) return true
        val version = extractVersion(path, type) ?: return false
        val parsed = SemanticVersion.parse(version) ?: return false
        return parsed >= MINIMUM_LUAROCKS_VERSION
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    /** Runs the version command and returns merged stdout+stderr output, or `null` on failure. */
    private fun runVersionCommand(path: String, type: LuaToolType): String? {
        val file = File(path)
        if (!file.exists() || !file.canExecute()) {
            LOG.debug("[$type] binary not found or not executable: $path")
            return null
        }

        val cmd = GeneralCommandLine(path).apply {
            addParameter(versionFlagFor(type))
            withWorkDirectory(file.parentFile)
        }

        val output = LuaProcessUtil.capture(cmd, VERSION_TIMEOUT_MS)
        if (output.exitCode == LuaProcessUtil.PROCESS_TIMEOUT_EXCEPTION_CODE) {
            LOG.warn("[$type] version check timed out for '$path'")
            return null
        }
        if (output.exitCode == LuaProcessUtil.PROCESS_EXECUTION_EXCEPTION_CODE) {
            LOG.warn("[$type] could not execute '$path'")
            return null
        }

        // Merge stdout + stderr; some tools (luacheck, LuaRocks) write to stderr
        val merged = buildString {
            val out = output.stdout.trim()
            val err = output.stderr.trim()
            if (out.isNotEmpty()) append(out)
            if (out.isNotEmpty() && err.isNotEmpty()) append('\n')
            if (err.isNotEmpty()) append(err)
        }

        LOG.debug("[$type] version output from '$path': $merged")
        return merged.ifEmpty { null }
    }

    private fun patternFor(type: LuaToolType): Pattern = when (type) {
        LuaToolType.LUAROCKS -> LUAROCKS_VERSION
        LuaToolType.LUACHECK -> LUACHECK_VERSION
        LuaToolType.STYLUA   -> STYLUA_VERSION
        LuaToolType.BUSTED   -> BUSTED_VERSION
        LuaToolType.LUACOV   -> LUACOV_VERSION
    }

    private fun versionFlagFor(type: LuaToolType): String = when (type) {
        LuaToolType.LUAROCKS -> "--version"
        LuaToolType.LUACHECK -> "--version"
        LuaToolType.STYLUA   -> "--version"
        LuaToolType.BUSTED   -> "--version"
        LuaToolType.LUACOV   -> "--help"
    }

    /** Minimal comparable semantic-version triple for minimum-version checks. */
    data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int {
            val cmp = major.compareTo(other.major)
            if (cmp != 0) return cmp
            val cmp2 = minor.compareTo(other.minor)
            if (cmp2 != 0) return cmp2
            return patch.compareTo(other.patch)
        }

        companion object {
            /** Parses "3.9.2", "3.9.2-1", "3.9" etc. Returns `null` on parse failure. */
            fun parse(version: String): SemanticVersion? {
                val clean = version.substringBefore('-').trim()
                val parts = clean.split('.')
                return try {
                    SemanticVersion(
                        parts.getOrNull(0)?.toInt() ?: return null,
                        parts.getOrNull(1)?.toInt() ?: 0,
                        parts.getOrNull(2)?.toInt() ?: 0,
                    )
                } catch (_: NumberFormatException) {
                    null
                }
            }
        }
    }
}
