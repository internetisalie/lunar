package net.internetisalie.lunar.rocks.browser

/**
 * A Marketplace search-result row (design §2.5). Wraps the parsed [LuaRockPackage] with two
 * mutable/derived UI flags: [installed] (cross-referenced against the canonical tree, flipped in
 * place after install/uninstall — design §3.4) and [hasUpdate] (design §3.2).
 *
 * Mutated only on the EDT by [LuaRocksBrowserModel].
 */
data class LuaRockRow(val pkg: LuaRockPackage, var installed: Boolean, val hasUpdate: Boolean = false)
