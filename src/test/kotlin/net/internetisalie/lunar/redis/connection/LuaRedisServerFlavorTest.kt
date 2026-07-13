package net.internetisalie.lunar.redis.connection

import net.internetisalie.lunar.platform.LuaPlatform
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Headless coverage of the centralized `INFO server` flavor heuristic (design §2.5, §3.3) —
 * REDIS-03 TC-FLV-1/TC-FLV-2. Pure parsing + mismatch predicate; no live socket.
 */
class LuaRedisServerFlavorTest {

    @Test
    fun detectsValkeyWhenValkeyVersionPresentAndMismatchesRedisTarget() {
        val body = "# Server\r\nredis_version:7.2.4\r\nvalkey_version:8.0.1\r\nredis_mode:standalone\r\n"

        val info = LuaRedisServerFlavor.detect(body)

        assertEquals(ServerFlavor.VALKEY, info.flavor)
        assertEquals("8.0.1", info.version)
        assertTrue(LuaRedisServerFlavor.mismatches(info.flavor, LuaPlatform.REDIS))
    }

    @Test
    fun detectsRedisWhenOnlyRedisVersionPresentAndDoesNotMismatchRedisTarget() {
        val body = "# Server\r\nredis_version:7.4.0\r\nredis_mode:standalone\r\n"

        val info = LuaRedisServerFlavor.detect(body)

        assertEquals(ServerFlavor.REDIS, info.flavor)
        assertEquals("7.4.0", info.version)
        assertFalse(LuaRedisServerFlavor.mismatches(info.flavor, LuaPlatform.REDIS))
    }

    @Test
    fun emptyBodyIsRedisWithNoVersionAndNoMismatch() {
        val info = LuaRedisServerFlavor.detect("")

        assertEquals(ServerFlavor.REDIS, info.flavor)
        assertEquals("", info.version)
        assertFalse(LuaRedisServerFlavor.mismatches(info.flavor, LuaPlatform.REDIS))
    }

    @Test
    fun mismatchTableIsSymmetricForKnownFlavorsAndSilentForOtherTargets() {
        assertTrue(LuaRedisServerFlavor.mismatches(ServerFlavor.REDIS, LuaPlatform.VALKEY))
        assertTrue(LuaRedisServerFlavor.mismatches(ServerFlavor.VALKEY, LuaPlatform.REDIS))
        assertFalse(LuaRedisServerFlavor.mismatches(ServerFlavor.REDIS, LuaPlatform.REDIS))
        assertFalse(LuaRedisServerFlavor.mismatches(ServerFlavor.VALKEY, LuaPlatform.VALKEY))
        assertFalse(LuaRedisServerFlavor.mismatches(ServerFlavor.VALKEY, LuaPlatform.STANDARD))
    }
}
