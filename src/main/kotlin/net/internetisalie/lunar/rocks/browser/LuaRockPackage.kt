package net.internetisalie.lunar.rocks.browser

/**
 * A single version/arch entry returned by `luarocks search --porcelain`.
 *
 * The `--porcelain` format emits one line per arch variant:
 * ```
 * <name> <version> <arch> <repo> [<namespace>]
 * ```
 * Multiple arch rows for the same `(name, version)` are collapsed by [LuaRocksSearchService]
 * into one entry in the results list; the version picker (ROCKS-02-06) lists the distinct
 * versions for a given package name.
 *
 * @param isInstalled set by [LuaRocksSearchService] after cross-referencing `luarocks list`.
 */
data class LuaRockPackage(
    val name: String,
    val version: String,
    val arch: String,
    val repo: String,
    val namespace: String,
    val isInstalled: Boolean = false,
)
