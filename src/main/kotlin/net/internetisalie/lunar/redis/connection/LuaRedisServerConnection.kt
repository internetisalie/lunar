package net.internetisalie.lunar.redis.connection

import net.internetisalie.lunar.redis.resp.RespEndpoint

/**
 * Immutable value model of one named Redis/Valkey server connection (design §2.4).
 *
 * Pure data — thread-agnostic and holds **no secret**: the AUTH password lives in
 * [LuaRedisCredentialStore] (design §2.9), keyed by [id]. Persisted (without the password) by
 * [LuaRedisConnectionSettings]; resolved by [id] as the public seam REDIS-02/REDIS-05 consume
 * (risks-and-gaps "Public Seams").
 */
data class LuaRedisServerConnection(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val database: Int,
    val username: String?,
    val provisioning: LuaRedisProvisioning,
) {

    /**
     * Adapter bridging the connection model to the Phase-2 [RespEndpoint] the [net.internetisalie.lunar.redis.resp.RespClient]
     * opens (design §2.3). Phase 2 shipped `RespClient.open(RespEndpoint)`; this side resolves the
     * secret from [LuaRedisCredentialStore] and folds it, together with the connection's host / port /
     * TLS / database / username, into the endpoint value object — keeping `open` unchanged.
     */
    fun toEndpoint(password: String?): RespEndpoint =
        RespEndpoint(
            host = host,
            port = port,
            tls = tls,
            database = database,
            username = username,
            password = password,
        )
}

/**
 * How a connection's server is provisioned (design §2.4). `Remote` connects to an existing server;
 * `LocalBinary` / `Docker` start a session-scoped server (REDIS-01 Phase 4 [LuaRedisServerLauncher]).
 */
sealed interface LuaRedisProvisioning {

    /** Connect to an already-running server at the connection's own host/port. */
    object Remote : LuaRedisProvisioning

    /** Launch a local server binary resolved through the toolchain (`redis-server` / `valkey-server`). */
    data class LocalBinary(val toolKindId: String) : LuaRedisProvisioning

    /** Launch a Docker container (`redis:8` / `valkey/valkey:8`) for the session. */
    data class Docker(val image: String) : LuaRedisProvisioning

    companion object {
        const val KIND_REMOTE: String = "REMOTE"
        const val KIND_LOCAL_BINARY: String = "LOCAL_BINARY"
        const val KIND_DOCKER: String = "DOCKER"
    }
}
