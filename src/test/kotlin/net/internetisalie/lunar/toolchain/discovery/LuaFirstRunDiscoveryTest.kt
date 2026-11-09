package net.internetisalie.lunar.toolchain.discovery

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.rocks.RockspecRuntimeTestSupport
import net.internetisalie.lunar.toolchain.registry.LuaToolchainAppState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry

/** BUG-63: an empty toolchain is discovered once, on first project open, without the Discover button. */
class LuaFirstRunDiscoveryTest : BasePlatformTestCase() {
    private val registry get() = LuaToolchainRegistry.getInstance()

    override fun setUp() {
        super.setUp()
        registry.loadState(LuaToolchainAppState())
        LuaFirstRunDiscovery.resetForTest()
    }

    override fun tearDown() {
        try {
            LuaFirstRunDiscovery.resetForTest()
            RockspecRuntimeTestSupport.reset(project)
        } finally {
            super.tearDown()
        }
    }

    fun testAnEmptyRegistryIsDiscoveredOnce() {
        var runs = 0
        val found =
            LuaFirstRunDiscovery.discoverIfEmpty(registry) {
                runs++
                RockspecRuntimeTestSupport.registerRealLuaRuntime(project)
            }
        val again = LuaFirstRunDiscovery.discoverIfEmpty(registry) { runs++ }

        assertEquals(1, runs)
        assertEquals(listOf("lua"), found?.map { it.kindId })
        assertNull(again)
    }

    fun testARegistryWithToolsIsLeftAlone() {
        RockspecRuntimeTestSupport.registerRealLuaRuntime(project)
        var runs = 0

        assertNull(LuaFirstRunDiscovery.discoverIfEmpty(registry) { runs++ })
        assertEquals(0, runs)
    }

    fun testToolsRemovedAfterTheFirstRunAreNotRediscovered() {
        LuaFirstRunDiscovery.discoverIfEmpty(registry) { }
        var runs = 0

        assertNull(LuaFirstRunDiscovery.discoverIfEmpty(registry) { runs++ })
        assertEquals(0, runs)
    }
}
