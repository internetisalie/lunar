package net.internetisalie.lunar.toolchain.ui

import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TOOLING-08 §3.1 coverage: classifier tiers, runtime-first ordering, and platform-server eviction
 * (TC 6, 7). Pure registry reads — no fixture needed.
 */
class LuaToolKindClassifierTest {

    @Test
    fun testCommonTierIsRuntimeFirstThenDeclaredTools() {
        val common = LuaToolKindClassifier.byTier()[LuaToolKindClassifier.Tier.COMMON].orEmpty().map { it.id }
        // runtimes first (lua, luajit, tarantool), then the declared common tools in declaration order
        assertEquals(
            listOf("lua", "luajit", "tarantool", "luarocks", "luacheck", "stylua", "busted"),
            common
        )
    }

    @Test
    fun testAdvancedTierHoldsLuacov() {
        val advanced = LuaToolKindClassifier.byTier()[LuaToolKindClassifier.Tier.ADVANCED].orEmpty().map { it.id }
        assertEquals(listOf("luacov"), advanced)
    }

    @Test
    fun testPlatformServersAreEvictedFromBindable_TC6() {
        val bindableIds = LuaToolKindClassifier.bindable().map { it.id }
        assertFalse("redis-server" in bindableIds, "redis-server must not be bindable")
        assertFalse("valkey-server" in bindableIds, "valkey-server must not be bindable")
    }

    @Test
    fun testPlatformServersStillInRegistry_TC7() {
        // Eviction is UI-only: the kinds remain registered/resolvable.
        assertTrue(LuaToolKindRegistry.all().any { it.id == "redis-server" })
        assertTrue(LuaToolKindRegistry.all().any { it.id == "valkey-server" })
        assertEquals(
            LuaToolKindClassifier.Tier.PLATFORM_SERVER,
            LuaToolKindClassifier.tierOf(LuaToolKindRegistry.findById("redis-server")!!)
        )
    }

    @Test
    fun testBindableIsCommonPlusAdvanced() {
        val expected =
            LuaToolKindClassifier.byTier()[LuaToolKindClassifier.Tier.COMMON].orEmpty() +
                LuaToolKindClassifier.byTier()[LuaToolKindClassifier.Tier.ADVANCED].orEmpty()
        assertEquals(expected.map { it.id }, LuaToolKindClassifier.bindable().map { it.id })
    }
}
