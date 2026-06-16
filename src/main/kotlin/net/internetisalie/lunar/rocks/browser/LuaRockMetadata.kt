package net.internetisalie.lunar.rocks.browser

/**
 * Rich metadata for a specific package version, parsed from `luarocks show --porcelain`.
 *
 * The porcelain format is tab-separated `key\tvalue[\tvalue2]` lines. All fields are nullable
 * except [name] and [version] (present in every well-formed response).
 */
data class LuaRockMetadata(
    val name: String,
    val version: String,
    val summary: String?,
    val detailed: String?,
    val license: String?,
    val homepage: String?,
    val issues: String?,
    val location: String?,
    val dependencies: List<String>,
    val modules: List<String>,
)
