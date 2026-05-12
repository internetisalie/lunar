package net.internetisalie.lunar.platform.target

/**
 * Represents a specific version of a Lua platform.
 *
 * Separates the user-visible version label from the stable resource path segment,
 * ensuring that UI text or file paths can be changed independently.
 *
 * @param label User-visible version label (displayed in UI). Examples: "5.1", "2.1", "7+", "latest"
 * @param pathSegment Stable path identifier for resource directories. Examples: "lua-5.1", "luajit-2.1", "redis-7"
 * @param luacheckStd Optional luacheck standard for this version. Examples: "lua51", "luajit", "redis5".
 *                     If null, no `--std` argument is passed to luacheck (use default).
 */
data class VersionEntry(
    val label: String,
    val pathSegment: String,
    val luacheckStd: String? = null
) {
    override fun toString(): String = label
}
