package net.internetisalie.lunar.tool

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * Discovers known Lua ecosystem tool binaries on the host system.
 *
 * Discovery strategy (TOOL-01-02):
 * 1. Primary: PATH scan via [PathEnvironmentVariableUtil.findInPath] (handles PATHEXT on Windows).
 * 2. Supplemental: well-known install directories (platform-specific).
 *
 * Only executable files are returned. Callers are responsible for validation/version extraction.
 *
 * Must be called from a background thread — never the EDT.
 */
object LuaToolDiscoveryService {

    private val LOG = logger<LuaToolDiscoveryService>()

    // ---------------------------------------------------------------------------
    // Well-known extra directories to search beyond PATH
    // ---------------------------------------------------------------------------

    private val EXTRA_DIRS_UNIX: List<String> = listOf(
        "/usr/bin",
        "/usr/local/bin",
        "/opt/bin",
        "/opt/local/bin",
        "/home/linuxbrew/.linuxbrew/bin",
    )

    private val EXTRA_DIRS_WINDOWS: List<String> = listOf(
        """C:\Program Files\LuaRocks""",
        """C:\Program Files\Lua""",
        """C:\ProgramData\chocolatey\bin""",
        """${System.getenv("APPDATA") ?: ""}\LuaRocks\bin""",
        """${System.getenv("USERPROFILE") ?: ""}\scoop\shims""",
    )

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Scans for all known Lua tool types and returns a list of discovered binary files.
     *
     * The returned list may contain multiple entries for the same [LuaToolType] if the tool
     * is installed in several locations. Duplicates (same canonical path) are suppressed.
     */
    fun discoverKnownTools(): List<DiscoveredTool> {
        val seen = LinkedHashSet<String>()
        val results = mutableListOf<DiscoveredTool>()

        for (descriptor in LuaToolDescriptor.DESCRIPTORS) {
            for (file in findCandidates(descriptor)) {
                val canonical = try {
                    file.canonicalPath
                } catch (_: Exception) {
                    file.absolutePath
                }
                if (seen.add(canonical)) {
                    LOG.debug("[${descriptor.toolType}] discovered at $canonical")
                    results += DiscoveredTool(descriptor.toolType, file)
                }
            }
        }

        return results
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun findCandidates(descriptor: LuaToolDescriptor): List<File> {
        val found = mutableListOf<File>()

        // 1. PATH-based resolution (uses OS PATHEXT on Windows automatically)
        val pathFile = descriptor.resolveOnPath()
        if (pathFile != null && pathFile.isExecutableFile()) {
            found += pathFile
        }

        // 2. Supplemental well-known directories
        val extraDirs = if (SystemInfo.isWindows) EXTRA_DIRS_WINDOWS else EXTRA_DIRS_UNIX
        for (dir in extraDirs) {
            for (candidate in descriptor.candidates()) {
                val file = File(dir, candidate)
                if (file.isExecutableFile()) {
                    found += file
                }
            }
        }

        return found
    }

    /** Returns `true` if this [File] is a regular file that can be executed. */
    private fun File.isExecutableFile(): Boolean = exists() && isFile && canExecute()

    // ---------------------------------------------------------------------------
    // Result type
    // ---------------------------------------------------------------------------

    /** A binary file found for a given [LuaToolType]. */
    data class DiscoveredTool(val type: LuaToolType, val file: File)
}
