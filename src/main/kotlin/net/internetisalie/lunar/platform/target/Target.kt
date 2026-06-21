package net.internetisalie.lunar.platform.target

import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform

/**
 * Represents a selected runtime target (platform + version combination).
 *
 * Provides deterministic derivation of language level and library paths with
 * no string manipulation of display values. Both platform and version are immutable
 * once created.
 *
 * @param platform The Lua platform (STANDARD, REDIS, LUAJIT, etc.)
 * @param version The specific version for the platform
 */
data class Target(
    val platform: LuaPlatform,
    val version: VersionEntry
) {
    /**
     * Derives the implicit Lua language level from this target.
     *
     * Mapping rules:
     * - Standard 5.1 -> LUA51
     * - Standard 5.2 -> LUA52
     * - Standard 5.3 -> LUA53
     * - Standard 5.4 -> LUA54
     * - Standard 5.5 -> LUA55
     * - LuaJIT (all versions) -> LUA51 (LuaJIT is based on Lua 5.1)
     * - Redis (all versions) -> LUA51
     * - Tarantool (all versions) -> LUA51
     * - OpenResty/NGX (all versions) -> LUA51 (OpenResty uses LuaJIT)
     * - Pandoc (all versions) -> LUA54
     *
     * @return The corresponding LuaLanguageLevel
     */
    fun getImplicitLanguageLevel(): LuaLanguageLevel {
        return when {
            platform == LuaPlatform.STANDARD && version.label == "5.1" -> LuaLanguageLevel.LUA51
            platform == LuaPlatform.STANDARD && version.label == "5.2" -> LuaLanguageLevel.LUA52
            platform == LuaPlatform.STANDARD && version.label == "5.3" -> LuaLanguageLevel.LUA53
            platform == LuaPlatform.STANDARD && version.label == "5.4" -> LuaLanguageLevel.LUA54
            platform == LuaPlatform.STANDARD && version.label == "5.5" -> LuaLanguageLevel.LUA55
            platform == LuaPlatform.LUAJIT -> LuaLanguageLevel.LUA51
            platform == LuaPlatform.REDIS -> LuaLanguageLevel.LUA51
            platform == LuaPlatform.TARANTOOL -> LuaLanguageLevel.LUA51
            platform == LuaPlatform.NGX -> LuaLanguageLevel.LUA51
            platform == LuaPlatform.PANDOC -> LuaLanguageLevel.LUA54
            else -> LuaLanguageLevel.LUA54  // Default fallback
        }
    }

    /**
     * Returns the library root path for this target in the unified runtime structure.
     *
     * Format: `runtime/{platform.pathSegment}/{version.pathSegment}`
     *
     * Examples:
     * - Target(STANDARD, "5.1") -> "runtime/standard/lua-5.1"
     * - Target(REDIS, "7+") -> "runtime/redis/redis-7"
     * - Target(LUAJIT, "2.1") -> "runtime/luajit/luajit-2.1"
     *
     * @return The library root path
     */
    fun getLibraryRootPath(): String =
        "runtime/${platform.pathSegment}/${version.pathSegment}"

    /**
     * Returns the luacheck standard for this target, or null if not specified.
     *
     * @return The luacheck std value, or null if unsupported
     */
    fun getLuacheckStd(): String? = version.luacheckStd

    companion object {
        /**
         * Creates a default Target (Standard Lua 5.4).
         */
        fun default(): Target {
            val defaultVersion = PlatformVersionRegistry.defaultVersion(LuaPlatform.STANDARD)
                ?: throw IllegalStateException("No default version found for STANDARD platform")
            return Target(LuaPlatform.STANDARD, defaultVersion)
        }
    }
}
