package net.internetisalie.lunar.toolchain.probe

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import net.internetisalie.lunar.toolchain.model.LanguageLevelRule
import net.internetisalie.lunar.toolchain.model.LuaRuntimeInfo
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.model.RuntimeProbeSpec
import net.internetisalie.lunar.toolchain.model.SemanticVersion
import net.internetisalie.lunar.util.LuaProcessUtil
import java.nio.file.Path

class LuaToolProbeImpl : LuaToolProbe {

    override fun probe(kind: LuaToolKind, binaryPath: Path): LuaToolProbeResult {
        ApplicationManager.getApplication()?.assertIsNonDispatchThread()

        val file = binaryPath.toFile()
        if (!file.exists() || !file.canExecute()) {
            return LuaToolProbeResult(
                ok = false,
                version = null,
                luaVersion = null,
                runtime = null,
                failure = "Not executable"
            )
        }

        val cmd = GeneralCommandLine(binaryPath.toString()).apply {
            addParameters(kind.probe.args)
            withWorkDirectory(file.parentFile)
        }

        val output = LuaProcessUtil.capture(cmd, kind.probe.timeoutMs)
        if (output.exitCode == LuaProcessUtil.PROCESS_TIMEOUT_EXCEPTION_CODE) {
            return LuaToolProbeResult(
                ok = false,
                version = null,
                luaVersion = null,
                runtime = null,
                failure = "Timeout"
            )
        }
        if (output.exitCode == LuaProcessUtil.PROCESS_EXECUTION_EXCEPTION_CODE) {
            return LuaToolProbeResult(
                ok = false,
                version = null,
                luaVersion = null,
                runtime = null,
                failure = "Not executable"
            )
        }

        val out = output.stdout.trim()
        val err = output.stderr.trim()
        val merged = buildString {
            if (out.isNotEmpty()) append(out)
            if (out.isNotEmpty() && err.isNotEmpty()) append('\n')
            if (err.isNotEmpty()) append(err)
        }

        return interpret(merged, kind)
    }

    fun interpret(merged: String, kind: LuaToolKind): LuaToolProbeResult {
        val versionMatch = kind.probe.versionRegex.find(merged) ?: return LuaToolProbeResult(
            ok = false,
            version = null,
            luaVersion = null,
            runtime = null,
            failure = getFirstNonBlankLine(merged)
        )
        val version = versionMatch.groupValues[1]
        val luaVersion = kind.probe.luaVersionRegex?.find(merged)?.groupValues?.get(1)

        val runtimeInfo = parseRuntime(merged, version, kind)
        if (kind.probe.runtime != null && runtimeInfo == null) {
            return LuaToolProbeResult(
                ok = false,
                version = null,
                luaVersion = null,
                runtime = null,
                failure = getFirstNonBlankLine(merged)
            )
        }

        if (isBelowMinVersion(version, kind.minVersion)) {
            return LuaToolProbeResult(
                ok = false,
                version = version,
                luaVersion = luaVersion,
                runtime = runtimeInfo,
                failure = getFirstNonBlankLine(merged)
            )
        }

        return LuaToolProbeResult(
            ok = true,
            version = version,
            luaVersion = luaVersion,
            runtime = runtimeInfo,
            failure = null
        )
    }

    private fun parseRuntime(merged: String, version: String, kind: LuaToolKind): LuaRuntimeInfo? {
        val runtimeSpec = kind.probe.runtime ?: return null
        val bannerLine = merged.lineSequence()
            .firstOrNull { kind.probe.versionRegex.containsMatchIn(it) }
            ?: merged.lineSequence().firstOrNull() ?: ""

        val firstToken = bannerLine.trim().substringBefore(' ')
        if (firstToken != runtimeSpec.productToken) {
            return null
        }

        val languageLevel = when (val rule = runtimeSpec.languageLevel) {
            is LanguageLevelRule.Fixed -> rule.level
            is LanguageLevelRule.ByVersionPrefix -> {
                rule.prefixes.firstOrNull { version.startsWith(it.first) }?.second ?: rule.fallback
            }
        }

        return LuaRuntimeInfo(
            product = runtimeSpec.productToken,
            version = version,
            languageLevel = languageLevel,
            platform = runtimeSpec.platform,
            banner = bannerLine
        )
    }

    private fun isBelowMinVersion(version: String, minVersion: SemanticVersion?): Boolean {
        if (minVersion == null) return false
        val parsedVersion = SemanticVersion.parse(version) ?: return false
        return parsedVersion < minVersion
    }

    private fun getFirstNonBlankLine(merged: String): String {
        return merged.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() } ?: "No output"
    }
}
