package net.internetisalie.lunar.tool.health

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.logger
import net.internetisalie.lunar.platform.Banner
import net.internetisalie.lunar.tool.LuaTool
import net.internetisalie.lunar.util.LuaProcessUtil
import java.io.File

/**
 * Two-stage health check for a registered [LuaTool] (TOOL-03, design §3.1).
 *
 * Stage 1 — fast check: file existence and executability (no subprocess).
 * Stage 2 — mtime gate: if the file has not changed since the last successful check, return
 *   the cached result without re-running the binary.
 * Stage 3 — slow check: run `<tool> --version`, parse the output via [Banner], and return the result.
 *
 * **Threading:** must be called from a background thread — never the EDT.
 */
object LuaToolHealthChecker {

    private val LOG = logger<LuaToolHealthChecker>()

    /** Timeout (ms) for the `--version` subprocess. */
    const val VERSION_TIMEOUT_MS: Int = 10_000

    /**
     * Check the health of [tool] and return a [HealthResult].
     *
     * The caller is responsible for writing the result back onto the [tool] fields
     * (`isValid`, `version`, `lastCheckedMtime`, `lastCheckReason`).
     */
    fun check(tool: LuaTool): HealthResult {
        // Stage 1: fast checks — file existence and executability
        val file = File(tool.path)
        if (!file.exists()) {
            return HealthResult(isValid = false, version = null, reason = "Binary missing")
        }
        if (!file.canExecute()) {
            return HealthResult(isValid = false, version = null, reason = "Permission denied")
        }

        // Stage 2: mtime gate — skip slow check if the binary has not changed
        val mtime = file.lastModified()
        if (tool.isValid && tool.version.isNotEmpty() && mtime == tool.lastCheckedMtime) {
            LOG.debug("[${tool.type}] mtime unchanged for '${tool.path}'; reusing cached result")
            return HealthResult(isValid = true, version = tool.version, reason = "OK ${tool.version}")
        }

        // Stage 3: slow check — run the binary with --version
        val cmd = GeneralCommandLine(tool.path).apply {
            addParameter("--version")
            withWorkDirectory(file.parentFile)
        }
        val output = LuaProcessUtil.capture(cmd, VERSION_TIMEOUT_MS)

        if (output.exitCode == LuaProcessUtil.PROCESS_TIMEOUT_EXCEPTION_CODE) {
            LOG.warn("[${tool.type}] --version timed out for '${tool.path}'")
            return HealthResult(isValid = false, version = null, reason = "Timeout")
        }
        if (output.exitCode == LuaProcessUtil.PROCESS_EXECUTION_EXCEPTION_CODE) {
            LOG.warn("[${tool.type}] could not execute '${tool.path}'")
            return HealthResult(isValid = false, version = null, reason = "Not executable")
        }

        val banner = Banner.create(output)
        return if (banner != null) {
            val ver = banner.version
            HealthResult(isValid = true, version = ver, reason = "OK $ver")
        } else {
            val errLine = output.stderr.lines().firstOrNull { it.isNotBlank() }
                ?: output.stdout.lines().firstOrNull { it.isNotBlank() }
                ?: "Not executable"
            HealthResult(isValid = false, version = null, reason = errLine)
        }
    }

    /**
     * Apply a [HealthResult] back onto the [tool], updating `isValid`, `version`,
     * `lastCheckedMtime`, and `lastCheckReason`.  Also captures the current file mtime so
     * subsequent calls can short-circuit when the binary is unchanged.
     */
    fun applyResult(tool: LuaTool, result: HealthResult) {
        tool.isValid = result.isValid
        if (result.version != null) tool.version = result.version
        tool.lastCheckReason = result.reason
        // Update mtime only when the fast-path passed (file existed)
        val mtime = File(tool.path).lastModified()
        if (mtime != 0L) tool.lastCheckedMtime = mtime
    }
}

/**
 * Result of a [LuaToolHealthChecker.check] call.
 *
 * @param isValid   `true` when the tool passed all checks.
 * @param version   Extracted version string, or `null` on failure.
 * @param reason    Human-readable status: "OK 1.1.0", "Binary missing", "Permission denied", etc.
 */
data class HealthResult(
    val isValid: Boolean,
    val version: String?,
    val reason: String,
)
