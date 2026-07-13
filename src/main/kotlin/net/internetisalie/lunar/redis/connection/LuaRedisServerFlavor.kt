package net.internetisalie.lunar.redis.connection

import net.internetisalie.lunar.platform.LuaPlatform

/** The server implementation flavor derived from an `INFO server` reply (design §2.5, §3.3). */
enum class ServerFlavor { REDIS, VALKEY }

/**
 * Flavor + reported version parsed from an `INFO server` body (design §2.5).
 *
 * [version] is the flavor-specific version string: `valkey_version` for a Valkey server, else
 * `redis_version`, else `""`.
 */
data class ServerFlavorInfo(val flavor: ServerFlavor, val version: String)

/**
 * Single source of truth for the `INFO server` flavor heuristic (design §2.5, §3.3).
 *
 * Centralizes the `valkey_version`-presence check that REDIS-01 §4.3 declared inline
 * ([RespServerInfo] now delegates its flavor derivation here). Pure functions on a [String] — no
 * I/O — so callers may invoke [detect] on the REDIS-01 pooled coroutine.
 */
object LuaRedisServerFlavor {

    private const val VALKEY_VERSION_KEY = "valkey_version"
    private const val REDIS_VERSION_KEY = "redis_version"

    /** Parses [infoServerBody] into a [ServerFlavorInfo]; `valkey_version` wins over `redis_version`. */
    fun detect(infoServerBody: String): ServerFlavorInfo {
        val fields = parseFields(infoServerBody)
        val valkeyVersion = fields[VALKEY_VERSION_KEY]
        if (valkeyVersion != null) return ServerFlavorInfo(ServerFlavor.VALKEY, valkeyVersion)
        val redisVersion = fields[REDIS_VERSION_KEY]
        if (redisVersion != null) return ServerFlavorInfo(ServerFlavor.REDIS, redisVersion)
        return ServerFlavorInfo(ServerFlavor.REDIS, "")
    }

    /**
     * True only when both sides are a known flavor and they disagree (design §3.3): a Valkey server
     * under a Redis target, or a Redis server under a Valkey target. Any other target opts out.
     */
    fun mismatches(detected: ServerFlavor, target: LuaPlatform): Boolean =
        (target == LuaPlatform.REDIS && detected == ServerFlavor.VALKEY) ||
            (target == LuaPlatform.VALKEY && detected == ServerFlavor.REDIS)

    private fun parseFields(infoServerBody: String): Map<String, String> =
        infoServerBody.split("\n")
            .map { it.trimEnd('\r') }
            .filter { it.contains(':') }
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
}
