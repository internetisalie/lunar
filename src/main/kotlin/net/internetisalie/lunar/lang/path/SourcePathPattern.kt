package net.internetisalie.lunar.lang.path

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.settings.expandMacros

data object PathConfiguration {
    const val DEFAULT_SOURCE_PATH = "/usr/local/share/lua/5.4/?.lua;" +
            "/usr/local/share/lua/5.4/?/init.lua;" +
            "/usr/local/lib/lua/5.4/?.lua;" +
            "/usr/local/lib/lua/5.4/?/init.lua;" +
            "\$PROJECT_DIR$/?.lua;" +
            "\$PROJECT_DIR$/?/init.lua"

    const val DIRECTORY_SEPARATOR = '/'
    const val TEMPLATE_SEPARATOR = ";"
    const val SUBSTITUTION_MARK = "?"

    fun getProjectSourcePathPatterns(project: Project): List<SourcePathPattern> {
        val userPatterns = getStaticSourcePathPatterns(project)
        val rockspecPatterns = net.internetisalie.lunar.rocks.RockspecSourcePathProvider.getInstance(project).derivedPatterns()
        return (userPatterns + rockspecPatterns).distinctBy { it.spec }
    }

    /**
     * User/settings-derived patterns only (the configured `package.path`); performs NO index-backed
     * rockspec discovery. Callers that run as an [com.intellij.openapi.roots.AdditionalLibraryRootsProvider]
     * MUST use this rather than [getProjectSourcePathPatterns]: the platform invokes that provider while
     * building the very index that rockspec discovery queries, so routing through the rockspec-aware
     * variant forms a CachedValue dependency cycle (RecursionManager StackOverflowPreventedException).
     */
    fun getStaticSourcePathPatterns(project: Project): List<SourcePathPattern> {
        val state = LuaProjectSettings.getInstance(project).state
        val luaPath = state.expandSourcePath(project)
            .ifEmpty { DEFAULT_SOURCE_PATH.expandMacros(project) }
        return SourcePathPattern.patternsFromLuaPath(luaPath)
    }
}

// A single entry in package.path template
data class SourcePathPattern(val spec: String) {
    val leadingPath : String
        get() = spec.substringBefore(PathConfiguration.SUBSTITUTION_MARK)

    // The literal portion after the substitution mark (e.g. ".lua" or "/init.lua").
    val suffix : String
        get() = spec.substringAfter(PathConfiguration.SUBSTITUTION_MARK)

    fun interpolate(packageName: String): String {
        return spec.replace(
            PathConfiguration.SUBSTITUTION_MARK,
            packageName.replace(
                '.',
                PathConfiguration.DIRECTORY_SEPARATOR,
            ),
        )
    }

    companion object {
        // Parse all patterns from a lua-style search path string
        fun patternsFromLuaPath(luaPath: String): List<SourcePathPattern> {
            return luaPath
                .split(PathConfiguration.TEMPLATE_SEPARATOR)
                .map { SourcePathPattern(it) }
        }
    }
}

