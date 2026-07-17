package net.internetisalie.lunar.toolchain.ui

import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry

/**
 * TOOLING-08 §2.1 / §3.1. Partitions the tool-kind registry into common / advanced / platform-server
 * tiers by capability, so both the project *Toolchain Bindings* split and the app *Global Default
 * Bindings* group share one classification. Name-independent: a future empty-capability kind
 * auto-excludes; a future capability'd kind lands in ADVANCED unless its id joins
 * [COMMON_TOOL_KIND_IDS].
 */
object LuaToolKindClassifier {

    enum class Tier { COMMON, ADVANCED, PLATFORM_SERVER }

    /** COMMON kind ids beyond the runtime; single source of truth (design §2.1). */
    val COMMON_TOOL_KIND_IDS: Set<String> = linkedSetOf("luarocks", "luacheck", "stylua", "busted")

    /**
     * Classifies a kind by capability (design §3.1):
     * 1. no capabilities → [Tier.PLATFORM_SERVER] (today `redis-server`, `valkey-server`).
     * 2. a runtime kind → [Tier.COMMON].
     * 3. id in [COMMON_TOOL_KIND_IDS] → [Tier.COMMON].
     * 4. otherwise → [Tier.ADVANCED].
     */
    fun tierOf(kind: LuaToolKind): Tier = when {
        kind.capabilities.isEmpty() -> Tier.PLATFORM_SERVER
        kind.isRuntime -> Tier.COMMON
        kind.id in COMMON_TOOL_KIND_IDS -> Tier.COMMON
        else -> Tier.ADVANCED
    }

    /** The kinds a bindings UI may show: COMMON + ADVANCED, runtime-first, platform servers excluded. */
    fun bindable(): List<LuaToolKind> =
        byTier().filterKeys { it != Tier.PLATFORM_SERVER }.values.flatten()

    /** Ordered tiers: runtime-first within COMMON, then the declared tools, advanced in registry order. */
    fun byTier(): Map<Tier, List<LuaToolKind>> {
        val all = LuaToolKindRegistry.all()
        val (runtimeKinds, otherKinds) = all.filter { tierOf(it) == Tier.COMMON }.partition { it.isRuntime }
        val commonTools = COMMON_TOOL_KIND_IDS.mapNotNull { id -> otherKinds.firstOrNull { it.id == id } }
        return linkedMapOf(
            Tier.COMMON to (runtimeKinds + commonTools),
            Tier.ADVANCED to all.filter { tierOf(it) == Tier.ADVANCED },
            Tier.PLATFORM_SERVER to all.filter { tierOf(it) == Tier.PLATFORM_SERVER }
        )
    }
}
