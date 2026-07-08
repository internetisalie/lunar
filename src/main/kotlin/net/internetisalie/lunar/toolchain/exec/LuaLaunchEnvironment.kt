package net.internetisalie.lunar.toolchain.exec

import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File
import java.nio.file.Path

/**
 * The computed environment-injection triple for a project's Lua subprocesses (TOOLING-03-10):
 * the PATH-prepend directories plus the derived `LUA_PATH`, `LUA_CPATH`, and `LUAROCKS_CONFIG`.
 *
 * A `null` [luaPath] / [luaCPath] / [luarocksConfig] means "leave the corresponding env var
 * untouched"; [applyTo] never clobbers an existing PATH — it prepends [pathPrependDirs] (highest
 * priority first) ahead of whatever PATH the command line or process already carries.
 *
 * Replaces the divergent per-consumer assemblies (`LuaToolEnvironment.prependToolDirsToPath` and
 * the inline LUA_PATH unions in the run configs); the PATH mechanics are verbatim from
 * `tool/LuaToolEnvironment.kt`.
 */
data class LuaLaunchEnvironment(
    val pathPrependDirs: List<Path>,
    val luaPath: String?,
    val luaCPath: String?,
    val luarocksConfig: String?,
) {
    /** Applies the injection triple to [commandLine] in place; returns it for chaining. */
    fun applyTo(commandLine: GeneralCommandLine): GeneralCommandLine {
        applyPath(commandLine)
        luaPath?.let { commandLine.environment["LUA_PATH"] = it }
        luaCPath?.let { commandLine.environment["LUA_CPATH"] = it }
        luarocksConfig?.let {
            if ("LUAROCKS_CONFIG" !in commandLine.environment) {
                commandLine.environment["LUAROCKS_CONFIG"] = it
            }
        }
        return commandLine
    }

    private fun applyPath(commandLine: GeneralCommandLine) {
        if (pathPrependDirs.isEmpty()) return
        val prefix = pathPrependDirs.joinToString(File.pathSeparator) { it.toString() }
        val existing = commandLine.environment["PATH"]
            ?: System.getenv("PATH")
            ?: ""
        commandLine.environment["PATH"] =
            if (existing.isBlank()) prefix else "$prefix${File.pathSeparator}$existing"
    }
}
