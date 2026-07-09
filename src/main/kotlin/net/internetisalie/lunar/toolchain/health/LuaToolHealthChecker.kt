package net.internetisalie.lunar.toolchain.health

import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.probe.LuaToolProbe
import net.internetisalie.lunar.toolchain.probe.LuaToolProbeResult
import java.io.File

object LuaToolHealthChecker {

    fun check(
        tool: LuaRegisteredTool,
        kind: LuaToolKind,
        probe: LuaToolProbe = LuaToolProbe.getInstance()
    ): LuaToolCheckResult {
        val targetFile = File(tool.path)
        val fastResult = evaluateFastChecks(targetFile)
        if (fastResult != null) return fastResult

        val mtime = targetFile.lastModified()
        if (isMtimeGateArmed(tool, mtime)) {
            return LuaToolCheckResult(tool.health, tool.version, tool.luaVersion, tool.runtime)
        }

        val probeResult = probe.probe(kind, targetFile.toPath())
        return buildProbeResult(probeResult, mtime)
    }

    private fun evaluateFastChecks(targetFile: File): LuaToolCheckResult? {
        if (!targetFile.exists()) {
            return LuaToolCheckResult(
                health = LuaToolHealth(fileExists = false, executable = false, probeOk = null, probedAtMtime = null, reason = "Binary missing"),
                version = null,
                luaVersion = null,
                runtime = null
            )
        }
        if (!targetFile.canExecute()) {
            return LuaToolCheckResult(
                health = LuaToolHealth(fileExists = true, executable = false, probeOk = null, probedAtMtime = null, reason = "Permission denied"),
                version = null,
                luaVersion = null,
                runtime = null
            )
        }
        return null
    }

    private fun isMtimeGateArmed(tool: LuaRegisteredTool, mtime: Long): Boolean =
        tool.health.probeOk == true &&
            tool.health.probedAtMtime == mtime &&
            tool.version != null

    private fun buildProbeResult(probeResult: LuaToolProbeResult, mtime: Long): LuaToolCheckResult {
        return if (probeResult.ok) {
            LuaToolCheckResult(
                health = LuaToolHealth(
                    fileExists = true,
                    executable = true,
                    probeOk = true,
                    probedAtMtime = mtime,
                    reason = "OK ${probeResult.version}"
                ),
                version = probeResult.version,
                luaVersion = probeResult.luaVersion,
                runtime = probeResult.runtime
            )
        } else {
            LuaToolCheckResult(
                health = LuaToolHealth(
                    fileExists = true,
                    executable = true,
                    probeOk = false,
                    probedAtMtime = mtime,
                    reason = probeResult.failure ?: "Not executable"
                ),
                version = null,
                luaVersion = null,
                runtime = null
            )
        }
    }
}
