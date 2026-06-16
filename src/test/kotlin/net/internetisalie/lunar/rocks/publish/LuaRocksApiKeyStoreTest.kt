package net.internetisalie.lunar.rocks.publish

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TC-ROCKS-08-02: the credential key must stay stable across releases so upgrades do not orphan a
 * previously-stored API key. This pins the constants (not the PasswordSafe round-trip, which needs
 * a running IDE).
 */
class LuaRocksApiKeyStoreTest {
    @Test
    fun subsystemAndKeyAreStable() {
        assertEquals("Lunar LuaRocks", LuaRocksApiKeyStore.SUBSYSTEM)
        assertEquals("luarocks.org API key", LuaRocksApiKeyStore.KEY)
    }

    @Test
    fun serviceNameEmbedsSubsystemAndKey() {
        val name = LuaRocksApiKeyStore.serviceName
        assertTrue(name.contains(LuaRocksApiKeyStore.SUBSYSTEM), "service name should embed the subsystem")
        assertTrue(name.contains(LuaRocksApiKeyStore.KEY), "service name should embed the key")
    }
}
