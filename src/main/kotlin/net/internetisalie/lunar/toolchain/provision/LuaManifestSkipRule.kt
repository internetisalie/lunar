package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path

/**
 * Inputs to the skip rule (design §3.1 step 8, §3.3), bundled so [LuaManifestSkipRule.shouldSkip]
 * stays a one-argument function (engineering-contract §2.1).
 */
data class LuaSkipCheck(
    val recorded: LuaManifestComponent,
    val freshHash: String,
    val rootDir: Path,
)

/**
 * Decides whether a component is already up to date (design §3.1 step 8): the freshly computed
 * identifiers hash matches the recorded one AND every recorded binary exists and is executable.
 *
 * Binaries are rootDir-relative; they are resolved against the environment root. Executability
 * is the POSIX exec bit on POSIX hosts; on Windows, existence is sufficient.
 */
object LuaManifestSkipRule {
    fun shouldSkip(check: LuaSkipCheck): Boolean {
        if (check.recorded.identifiersHash != check.freshHash) {
            return false
        }
        return check.recorded.binaries.all { relative ->
            isExecutable(check.rootDir.resolve(relative))
        }
    }

    private fun isExecutable(path: Path): Boolean =
        Files.isRegularFile(path) && (SystemInfo.isWindows || Files.isExecutable(path))
}
