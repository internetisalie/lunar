package net.internetisalie.lunar.rocks

import com.intellij.openapi.diagnostic.logger
import java.nio.file.FileSystems
import java.nio.file.Path

/**
 * Decides whether a project-relative rockspec path is included in discovery (ROCKS-09-02 / -07).
 *
 * Built-in exclusion is by **directory segment**, never by filename: a file literally named
 * `build-tools-1.0-1.rockspec` in a non-excluded directory is still discovered; only a
 * `build`-prefixed, `lua_modules`, `.luarocks`, `output`, or `thirdparty` **directory** in its
 * path excludes it. Optional per-project override globs (ROCKS-09-07) refine the built-in result.
 *
 * Pure: no platform access; operates on `/`-normalised relative path strings.
 */
object RockspecExclusionFilter {
    private val log = logger<RockspecExclusionFilter>()

    val BUILT_IN_EXCLUDED_SEGMENTS = listOf("lua_modules", ".luarocks", "output", "thirdparty")
    const val BUILD_SEGMENT_PREFIX = "build"

    /**
     * @param relativePath project-relative, `/`-normalised (e.g. `rocks/adt/adt-1.0-1.rockspec`)
     * @param includeGlobs when non-empty, acts as an allow-list (ROCKS-09-07)
     * @param excludeGlobs when non-empty, any match excludes the path (ROCKS-09-07)
     */
    fun isIncluded(relativePath: String, includeGlobs: List<String>, excludeGlobs: List<String>): Boolean {
        if (!isBuiltInIncluded(relativePath)) return false
        if (includeGlobs.isEmpty() && excludeGlobs.isEmpty()) return true
        if (excludeGlobs.any { matchesGlob(relativePath, it) }) return false
        return includeGlobs.isEmpty() || includeGlobs.any { matchesGlob(relativePath, it) }
    }

    private fun isBuiltInIncluded(relativePath: String): Boolean {
        val directorySegments = relativePath.split('/').dropLast(1)
        return directorySegments.none { isExcludedSegment(it) }
    }

    private fun isExcludedSegment(segment: String): Boolean {
        val normalized = segment.lowercase()
        return normalized in BUILT_IN_EXCLUDED_SEGMENTS || normalized.startsWith(BUILD_SEGMENT_PREFIX)
    }

    private fun matchesGlob(relativePath: String, glob: String): Boolean =
        try {
            FileSystems.getDefault().getPathMatcher("glob:$glob").matches(Path.of(relativePath))
        } catch (e: IllegalArgumentException) {
            log.warn("Ignoring invalid rockspec glob '$glob': ${e.message}")
            false
        }
}
