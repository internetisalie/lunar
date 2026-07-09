package net.internetisalie.lunar.toolchain.provision

import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedVersionComparator
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeedLoader

/**
 * Feed-derived, Swing-free catalog backing the provisioning dialogs (design §2.12, §2.13).
 *
 * Reads only the bundled feed (no I/O beyond the cached resource) and classifies provisionable
 * kinds for the [LuaProvisionDialog] combos, the forced-LuaRocks rule, and `toRequest` ordering.
 * Classification mirrors [LuaProvisioningPlan]: a tool is a *rock tool* when its only strategy is
 * `luarocks-install`, otherwise a *release-binary tool*.
 */
object LuaToolCatalog {
    /** Runtime kinds offered in the Runtime combo (v1: `lua` + `luajit` — its feed gate is open). */
    val RUNTIME_KINDS: List<String> = listOf("lua", "luajit")

    /** Dev-tool kinds offered as checkboxes, in display order (design §2.12 field 6). */
    val TOOL_KINDS: List<String> = listOf("luacheck", "stylua", "busted", "luacov", "lua-language-server")

    /** Tools installed through LuaRocks (only strategy is `luarocks-install`). */
    val ROCK_TOOL_KINDS: List<String> =
        TOOL_KINDS.filter { LuaProvisioningPlan.strategyIdsFor(it) == listOf("luarocks-install") }

    /** Tools acquired as a release binary (any tool that is not a rock tool). */
    val RELEASE_BINARY_TOOL_KINDS: List<String> = TOOL_KINDS.filter { it !in ROCK_TOOL_KINDS }

    /** Visible (un-gated) versions of [kindId] sorted newest-first (design §3.11 descending). */
    fun visibleVersions(feed: LuaToolchainFeed, kindId: String): List<String> {
        val kind = feed.kinds[kindId] ?: return emptyList()
        return kind.versions
            .filter { it.gatedOn == null }
            .map { it.version }
            .sortedWith(LuaFeedVersionComparator.reversed())
    }

    /** The version the `latest` alias resolves to for [kindId], or the newest visible version. */
    fun defaultVersion(feed: LuaToolchainFeed, kindId: String, platform: LuaHostPlatform): String {
        val resolved = runCatching {
            LuaToolchainFeedLoader.resolveVersion(feed, kindId, "latest", platform).version
        }.getOrNull()
        return resolved ?: visibleVersions(feed, kindId).firstOrNull().orEmpty()
    }
}
