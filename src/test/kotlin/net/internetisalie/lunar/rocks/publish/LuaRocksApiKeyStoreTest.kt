package net.internetisalie.lunar.rocks.publish

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TC 7, TC 8: headless structural checks for [LuaRocksApiKeyStore].
 *
 * TC 7: per-server key pattern `"luarocks API key:<server>"` is stable.
 * TC 8: null/blank server falls through to [LuaRocksApiKeyStore.LEGACY_KEY] (back-compat).
 *
 * PasswordSafe round-trips require a running IDE; those are covered in the human-verification
 * checklist (scenarios 4.1–4.3). These tests pin the key derivation contract.
 */
class LuaRocksApiKeyStoreTest {

    // ── TC 8: legacy key fall-through ────────────────────────────────────────

    @Test
    fun subsystemIsStable() {
        assertEquals("Lunar LuaRocks", LuaRocksApiKeyStore.SUBSYSTEM)
    }

    @Test
    fun legacyKeyIsStable() {
        assertEquals("luarocks.org API key", LuaRocksApiKeyStore.LEGACY_KEY)
    }

    @Test
    fun keyForNullReturnLegacyKey() {
        assertEquals(LuaRocksApiKeyStore.LEGACY_KEY, LuaRocksApiKeyStore.keyFor(null))
    }

    @Test
    fun keyForBlankReturnLegacyKey() {
        assertEquals(LuaRocksApiKeyStore.LEGACY_KEY, LuaRocksApiKeyStore.keyFor(""))
    }

    @Test
    fun keyForWhitespaceReturnLegacyKey() {
        assertEquals(LuaRocksApiKeyStore.LEGACY_KEY, LuaRocksApiKeyStore.keyFor("   "))
    }

    // ── TC 7: per-server key pattern ─────────────────────────────────────────

    @Test
    fun keyForServerReturnsPerServerKey() {
        val server = "http://localhost:8080"
        assertEquals("luarocks API key:$server", LuaRocksApiKeyStore.keyFor(server))
    }

    @Test
    fun keyForDifferentServersProduceDifferentKeys() {
        val key1 = LuaRocksApiKeyStore.keyFor("http://localhost:8080")
        val key2 = LuaRocksApiKeyStore.keyFor("https://reg.example")
        assertTrue(key1 != key2, "different servers must produce different credential keys")
    }

    @Test
    fun serviceNameEmbedsSubsystemAndLegacyKey() {
        val name = LuaRocksApiKeyStore.serviceName
        assertTrue(name.contains(LuaRocksApiKeyStore.SUBSYSTEM), "service name should embed the subsystem")
        assertTrue(name.contains(LuaRocksApiKeyStore.LEGACY_KEY), "service name should embed the legacy key")
    }

    @Test
    fun perServerKeyDiffersFromLegacyKey() {
        val legacyKey = LuaRocksApiKeyStore.keyFor(null)
        val serverKey = LuaRocksApiKeyStore.keyFor("https://reg.example")
        assertTrue(legacyKey != serverKey, "per-server key must differ from legacy key")
    }
}
