package net.internetisalie.lunar.toolchain.provision

/**
 * Local strategy-selection table (design §3.12, Phase-0 reconciliation revised at Phase 6).
 *
 * The ordered strategy ids per `kindId` live here — in the `toolchain.provision` package — and
 * NOT on `LuaToolKind.provisioning` (which stays `emptyList()`, untouched). Rationale: the
 * registry kind set diverges from the provisionable set (`lua-language-server` is provisionable
 * but has no registered kind; `tarantool` is a registered kind that is not provisionable), so a
 * per-kind-descriptor field cannot represent this order without touching the shared TOOLING-01
 * model. The POSIX-vs-Windows split in the design table is enforced by each strategy's
 * `supports()` (Windows source-build, missing assets, closed gates), so this table lists the
 * union of ids for both platforms; unsupported entries are skipped during selection (§3.12).
 */
object LuaProvisioningPlan {
    /** Kinds whose selected strategy set includes [SourceBuildStrategy.id]; drives §3.1 step 6 preflight. */
    val SOURCE_BUILD_KINDS: Set<String> = setOf("lua", "luajit", "luarocks")

    private val STRATEGY_ORDER: Map<String, List<String>> = mapOf(
        "lua" to listOf("source-build", "release-binary"),
        "luajit" to listOf("source-build"),
        "luarocks" to listOf("source-build", "release-binary"),
        "luacheck" to listOf("release-binary", "luarocks-install"),
        "stylua" to listOf("release-binary"),
        "lua-language-server" to listOf("release-binary"),
        "busted" to listOf("luarocks-install"),
        "luacov" to listOf("luarocks-install"),
    )

    /** The runtime kinds ordered first in §3.1 step 5. */
    val RUNTIME_KINDS: Set<String> = setOf("lua", "luajit")

    /** The ordered strategy ids for [kindId]; empty when the kind is not provisionable. */
    fun strategyIdsFor(kindId: String): List<String> = STRATEGY_ORDER[kindId].orEmpty()
}
