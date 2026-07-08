package net.internetisalie.lunar.toolchain.provision

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import java.nio.file.Path

/**
 * POSIX C-toolchain preflight (design §2.8, TOOLING-04-05). Presence-only — it locates the
 * compiler, archiver, ranlib and (optionally) `make` on `PATH` and never parses versions.
 * There is no MSVC bootstrap; Windows builds are prebuilt-only.
 *
 * Locates candidates via [PathEnvironmentVariableUtil.findInPath] (idiom:
 * `rocks/env/HererocksLocator.kt:36`). Runs off the EDT with the rest of provisioning.
 */
object LuaCompilerProbe {
    /** The resolved toolchain executables; [make] is null when `make` is absent (required only by LuaRocks/LuaJIT). */
    data class Toolchain(val cc: Path, val ar: Path, val ranlib: Path, val make: Path?)

    const val REMEDIATION = "No C toolchain found on PATH (need cc/gcc, ar, ranlib). " +
        "Install build tools (Linux: `sudo apt install build-essential`; macOS: " +
        "`xcode-select --install`) or pick a version with a prebuilt binary."

    fun probe(platform: LuaHostPlatform): Toolchain? {
        val ccCandidates = if (platform.os == LuaOs.MACOS) listOf("cc", "gcc") else listOf("gcc", "cc")
        val cc = firstInPath(ccCandidates) ?: return null
        val ar = inPath("ar") ?: return null
        val ranlib = inPath("ranlib") ?: return null
        return Toolchain(cc, ar, ranlib, inPath("make"))
    }

    private fun firstInPath(names: List<String>): Path? = names.firstNotNullOfOrNull(::inPath)

    private fun inPath(name: String): Path? = PathEnvironmentVariableUtil.findInPath(name)?.toPath()
}
