package net.internetisalie.lunar.redis.connection

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Headless coverage of the `INFO server` flavor/version parse (design §4.3). The live Test Connection
 * socket path is human-checklist verified (§1); this pins the pure parsing contract.
 */
class TestRespServerInfo {

    @Test
    fun parsesRedisVersionAndFlavor() {
        val body = "# Server\r\nredis_version:7.4.0\r\nredis_mode:standalone\r\n"

        val info = RespServerInfo.parse(body)

        assertEquals("Redis", info.flavor)
        assertEquals("7.4.0", info.version)
    }

    @Test
    fun valkeyVersionSelectsValkeyFlavor() {
        val body = "redis_version:7.2.4\r\nvalkey_version:8.0.0\r\n"

        val info = RespServerInfo.parse(body)

        assertEquals("Valkey", info.flavor)
        assertEquals("7.2.4", info.version)
    }

    @Test
    fun valkeyOnlyBodyFallsBackToValkeyVersion() {
        val body = "valkey_version:8.1.0\r\n"

        val info = RespServerInfo.parse(body)

        assertEquals("Valkey", info.flavor)
        assertEquals("8.1.0", info.version)
    }

    @Test
    fun missingVersionYieldsUnknown() {
        val info = RespServerInfo.parse("# Server\r\nredis_mode:standalone\r\n")

        assertEquals("Redis", info.flavor)
        assertEquals("unknown", info.version)
    }
}
