package net.internetisalie.lunar.rocks.browser

import net.internetisalie.lunar.rocks.deps.LuaRocksVersion

/**
 * Decides whether an installed rock has a newer available version (ROCKS-16-12, design §2.4 / §3.2).
 *
 * Pure — comparison uses [LuaRocksVersion] (LuaRocks version semantics). A malformed version parses
 * to a zero-valued [LuaRocksVersion], so two malformed versions compare equal (no spurious update).
 */
object LuaRocksUpdateDetector {

    /**
     * `true` when [latestAvailable] is a strictly greater version than [installedVersion];
     * `false` when [latestAvailable] is null or not greater (design §3.2).
     */
    fun hasUpdate(installedVersion: String, latestAvailable: String?): Boolean {
        if (latestAvailable == null) return false
        val installed = LuaRocksVersion.parse(installedVersion)
        val latest = LuaRocksVersion.parse(latestAvailable)
        return installed.compareTo(latest) < 0
    }

    /**
     * The maximum version across [rows] (caller-scoped to one package name), or `null` when empty.
     * Used to compute the update target for the Update button (design §3.2).
     */
    fun latestOf(rows: List<LuaRockRow>): String? = rows
        .map { LuaRocksVersion.parse(it.pkg.version) }
        .maxOrNull()
        ?.raw
}
