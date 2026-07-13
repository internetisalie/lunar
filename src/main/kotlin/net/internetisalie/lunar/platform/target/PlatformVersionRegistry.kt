package net.internetisalie.lunar.platform.target

import net.internetisalie.lunar.platform.LuaPlatform

/**
 * Static registry of all supported Lua platform versions.
 *
 * Pre-populated at plugin initialization with all available targets. Each platform
 * has one or more versions, each version has an explicit label (for UI) and path segment
 * (for resources). The two are never derived from one another.
 *
 * This is a singleton object; access via `PlatformVersionRegistry.getVersions(platform)`.
 */
object PlatformVersionRegistry {
    private val registry = mapOf(
        LuaPlatform.STANDARD to listOf(
            VersionEntry("5.1", "lua-5.1", luacheckStd = "lua51"),
            VersionEntry("5.2", "lua-5.2", luacheckStd = "lua52"),
            VersionEntry("5.3", "lua-5.3", luacheckStd = "lua53"),
            VersionEntry("5.4", "lua-5.4", luacheckStd = "lua54"),
            VersionEntry("5.5", "lua-5.5", luacheckStd = "lua54"),  // Lua 5.5 uses lua54 std (5.5 support is future)
        ),
        LuaPlatform.LUAJIT to listOf(
            VersionEntry("2.0", "luajit-2.0", luacheckStd = "luajit"),
            VersionEntry("2.1", "luajit-2.1", luacheckStd = "luajit"),
        ),
        LuaPlatform.REDIS to listOf(
            VersionEntry("5", "redis-5", luacheckStd = "redis5"),
            VersionEntry("6", "redis-6", luacheckStd = "redis6"),
            VersionEntry("7+", "redis-7", luacheckStd = "redis7"),
        ),
        LuaPlatform.VALKEY to listOf(
            VersionEntry("7.2", "valkey-7.2", luacheckStd = "redis7"),
            VersionEntry("8", "valkey-8", luacheckStd = "redis7"),
        ),
        LuaPlatform.TARANTOOL to listOf(
            VersionEntry("2.10", "tarantool-2.10", luacheckStd = null),
        ),
        LuaPlatform.NGX to listOf(
            VersionEntry("latest", "ngx-latest", luacheckStd = null),
        ),
        LuaPlatform.PANDOC to listOf(
            VersionEntry("latest", "pandoc-latest", luacheckStd = null),
        ),
    )

    /**
     * Returns all available versions for a given platform.
     *
     * @param platform The platform to query
     * @return List of VersionEntry objects for the platform, or empty list if platform not found
     */
    fun getVersions(platform: LuaPlatform): List<VersionEntry> =
        registry[platform] ?: emptyList()

    /**
     * Returns the default (first) version for a given platform.
     *
     * @param platform The platform to query
     * @return The default VersionEntry, or null if platform has no versions
     */
    fun defaultVersion(platform: LuaPlatform): VersionEntry? =
        registry[platform]?.firstOrNull()

    /**
     * Finds a specific version by platform and version label.
     *
     * @param platform The platform to query
     * @param versionLabel The version label to find (e.g., "5.1", "2.1", "7+")
     * @return The VersionEntry if found, or null if not found
     */
    fun findVersion(platform: LuaPlatform, versionLabel: String): VersionEntry? =
        registry[platform]?.find { it.label == versionLabel }

    /**
     * Resolves a concrete [Target] for a platform + version label, falling back gracefully
     * (ROCKS-16). Tries an exact [findVersion] match, then the platform's [defaultVersion], then the
     * global [Target.default]. This is the authoritative platform/label → Target mapping used by the
     * env-managed cascade so an env whose exact version isn't registered still resolves.
     *
     * @param platform The platform to resolve against
     * @param versionLabel The version label to find (e.g., "5.1", "2.1")
     * @return A non-null Target
     */
    fun resolveTarget(platform: LuaPlatform, versionLabel: String): Target {
        val version = findVersion(platform, versionLabel)
            ?: defaultVersion(platform)
            ?: return Target.default()
        return Target(platform, version)
    }

    /**
     * Returns all registered platforms.
     *
     * @return Set of all LuaPlatform values
     */
    fun platforms(): Set<LuaPlatform> = registry.keys

    /**
     * Returns whether a platform is supported.
     *
     * @param platform The platform to check
     * @return true if the platform has at least one version, false otherwise
     */
    fun isSupported(platform: LuaPlatform): Boolean =
        registry.containsKey(platform) && registry[platform]!!.isNotEmpty()
}
