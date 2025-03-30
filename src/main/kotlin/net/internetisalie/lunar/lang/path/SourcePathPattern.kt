package net.internetisalie.lunar.lang.path

data object PathConfiguration {
    const val DEFAULT_SOURCE_PATH = "/usr/local/share/lua/5.4/?.lua;" +
            "/usr/local/share/lua/5.4/?/init.lua;" +
            "/usr/local/lib/lua/5.4/?.lua;" +
            "/usr/local/lib/lua/5.4/?/init.lua;" +
            "/usr/share/lua/5.4/?.lua;" +
            "/usr/share/lua/5.4/?/init.lua;" +
            "./?.lua;" +
            "./?/init.lua"

    const val DIRECTORY_SEPARATOR = '/'
    const val TEMPLATE_SEPARATOR = ";"
    const val SUBSTITUTION_MARK = "?"

    fun getDefaultSourcePathPatterns(): List<SourcePathPattern> {
        return SourcePathPattern.patternsFromLuaPath(DEFAULT_SOURCE_PATH)
    }
}

// A single entry in package.path template
data class SourcePathPattern(val spec: String) {
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

