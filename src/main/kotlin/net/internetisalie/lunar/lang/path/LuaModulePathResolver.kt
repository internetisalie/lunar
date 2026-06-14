package net.internetisalie.lunar.lang.path

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Converts a [VirtualFile] to a dot-separated Lua module path by reversing
 * [SourcePathPattern.interpolate].
 *
 * `interpolate` substitutes the package name (with `.` -> `/`) into the spec's `?`
 * mark, so resolution strips the pattern's leading path and suffix and converts the
 * remaining `/` separators back to `.`. Implements COMP-03-03 path resolution
 * (COMP-03-AC-03), including `init.lua` normalization (TC-03-05).
 */
class LuaModulePathResolver {

    fun resolve(file: VirtualFile, project: Project): String? {
        if (!file.isValid) return null
        val filePath = file.path
        val patterns = PathConfiguration.getProjectSourcePathPatterns(project)

        val module = resolveFromPatterns(filePath, patterns) ?: resolveFromProjectBase(filePath, project)
        return module?.takeIf { it.isNotEmpty() }
    }

    /**
     * Longest-prefix root match: among matching patterns, the most specific leading
     * path wins (avoids a short stdlib prefix shadowing a deeper project root). Ties on
     * leading path prefer the longer suffix (so `?/init.lua` beats `?.lua` for an
     * `init.lua` file, yielding `dir.subdir` instead of `dir.subdir.init`).
     */
    private fun resolveFromPatterns(filePath: String, patterns: List<SourcePathPattern>): String? {
        val match = patterns
            .filter { filePath.startsWith(it.leadingPath) && filePath.endsWith(it.suffix) }
            .maxWithOrNull(
                compareBy<SourcePathPattern>({ it.leadingPath.length }, { it.suffix.length }),
            ) ?: return null

        val middle = filePath
            .removePrefix(match.leadingPath)
            .removeSuffix(match.suffix)
        return normalizeModule(middle)
    }

    private fun resolveFromProjectBase(filePath: String, project: Project): String? {
        val base = project.basePath ?: return null
        log.warn("No source path pattern matched $filePath; using project-relative fallback")
        val relative = filePath.removePrefix("$base/").removeSuffix(".lua")
        return normalizeModule(relative)
    }

    /** Convert directory separators to module dots and drop a trailing `init` segment. */
    private fun normalizeModule(rawPath: String): String {
        val dotted = rawPath
            .trim(PathConfiguration.DIRECTORY_SEPARATOR)
            .replace(PathConfiguration.DIRECTORY_SEPARATOR, '.')
        return dotted.removeSuffix(".init").let { if (it == "init") "" else it }
    }

    private companion object {
        val log = logger<LuaModulePathResolver>()
    }
}
