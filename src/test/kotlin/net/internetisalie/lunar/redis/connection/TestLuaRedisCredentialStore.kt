package net.internetisalie.lunar.redis.connection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * TC-CONN-2 (design §2.9): [LuaRedisCredentialStore] stores, reads, and clears a password through the
 * platform [com.intellij.ide.passwordSafe.PasswordSafe].
 *
 * A light platform fixture supplies the in-memory test PasswordSafe. The store is keyed by connection
 * id, so two connections keep independent secrets; a blank set clears the stored password (returns
 * `null`). Key-derivation stability is pinned headlessly, mirroring `LuaRocksApiKeyStoreTest`.
 */
class TestLuaRedisCredentialStore : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            LuaRedisCredentialStore.setPassword("u1", null)
            LuaRedisCredentialStore.setPassword("u2", null)
        } finally {
            super.tearDown()
        }
    }

    fun testSetThenGetReturnsStoredPassword() {
        LuaRedisCredentialStore.setPassword("u1", "s3cr3t")

        assertEquals("s3cr3t", LuaRedisCredentialStore.getPassword("u1"))
    }

    fun testBlankSetClearsStoredPassword() {
        LuaRedisCredentialStore.setPassword("u1", "s3cr3t")
        assertEquals("s3cr3t", LuaRedisCredentialStore.getPassword("u1"))

        LuaRedisCredentialStore.setPassword("u1", "")

        assertNull(LuaRedisCredentialStore.getPassword("u1"))
    }

    fun testNullSetClearsStoredPassword() {
        LuaRedisCredentialStore.setPassword("u1", "s3cr3t")

        LuaRedisCredentialStore.setPassword("u1", null)

        assertNull(LuaRedisCredentialStore.getPassword("u1"))
    }

    fun testPasswordsAreIsolatedPerConnectionId() {
        LuaRedisCredentialStore.setPassword("u1", "one")
        LuaRedisCredentialStore.setPassword("u2", "two")

        assertEquals("one", LuaRedisCredentialStore.getPassword("u1"))
        assertEquals("two", LuaRedisCredentialStore.getPassword("u2"))
    }

    fun testSubsystemIsStable() {
        assertEquals("Lunar Redis", LuaRedisCredentialStore.SUBSYSTEM)
    }

    fun testKeyEmbedsConnectionId() {
        assertEquals("redis connection:u1", LuaRedisCredentialStore.keyFor("u1"))
        assertTrue(LuaRedisCredentialStore.keyFor("u1") != LuaRedisCredentialStore.keyFor("u2"))
    }
}
