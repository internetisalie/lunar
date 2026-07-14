package net.internetisalie.lunar.redis.functions

import net.internetisalie.lunar.redis.connection.LuaRedisCredentialStore
import net.internetisalie.lunar.redis.connection.LuaRedisServerConnection
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespValue

/**
 * Panel-facing server operations for the Redis Functions tool window (design §2.9, §3.9).
 *
 * Each method opens a [RespClient] per call, performs one operation, and disposes the client in a
 * `finally` block — no retained client field. The command logic is isolated so tests can inject a
 * fake [RespClient] via the internal `*WithClient` overloads (testability seam, design §2.9).
 *
 * All methods are `suspend`; run them on a pooled coroutine and marshal results to the EDT in the
 * caller (engineering-contract §2). No retained heavy refs beyond the transient connection value.
 */
class LuaRedisFunctionsController {

    /**
     * Sends `FUNCTION LIST [WITHCODE]` and returns parsed [RedisLibraryEntry] list (design §3.9, TC-PANEL-1).
     *
     * Opens and disposes a [RespClient] per call. Callers must hold a reference to [connection]
     * for the lifetime of the call only.
     */
    suspend fun list(connection: LuaRedisServerConnection, withCode: Boolean): List<RedisLibraryEntry> {
        val client = openClient(connection)
        return try {
            listWithClient(client, withCode)
        } finally {
            client.dispose()
        }
    }

    /** Testable inner: performs `FUNCTION LIST` on an injected [client] (TC-PANEL-1). */
    suspend fun listWithClient(client: RespClient, withCode: Boolean): List<RedisLibraryEntry> {
        val args = if (withCode) listOf("FUNCTION", "LIST", "WITHCODE") else listOf("FUNCTION", "LIST")
        val reply = client.command(args.map { it.toByteArray(Charsets.UTF_8) })
        return LuaRedisFunctionListParser.parse(reply)
    }

    /**
     * Sends `FUNCTION DELETE <libraryName>` and returns the server reply (design §3.9, TC-PANEL-2).
     *
     * The caller removes the row from the model on a non-error reply. Opens and disposes a client per call.
     */
    suspend fun delete(connection: LuaRedisServerConnection, libraryName: String): RespValue {
        val client = openClient(connection)
        return try {
            deleteWithClient(client, libraryName)
        } finally {
            client.dispose()
        }
    }

    /** Testable inner: performs `FUNCTION DELETE` on an injected [client] (TC-PANEL-2). */
    suspend fun deleteWithClient(client: RespClient, libraryName: String): RespValue =
        client.command("FUNCTION", "DELETE", libraryName)

    /**
     * Sends `FUNCTION LOAD REPLACE <libraryBody>` and returns the server reply (design §3.9, TC-PANEL-3).
     *
     * On success the panel should call `refresh()`. Opens and disposes a client per call.
     */
    suspend fun deploy(connection: LuaRedisServerConnection, libraryBody: String): RespValue {
        val client = openClient(connection)
        return try {
            deployWithClient(client, libraryBody)
        } finally {
            client.dispose()
        }
    }

    /** Testable inner: performs `FUNCTION LOAD REPLACE` on an injected [client] (TC-PANEL-3). */
    suspend fun deployWithClient(client: RespClient, libraryBody: String): RespValue {
        val args = listOf("FUNCTION", "LOAD", "REPLACE", libraryBody)
        return client.command(args.map { it.toByteArray(Charsets.UTF_8) })
    }

    private suspend fun openClient(connection: LuaRedisServerConnection): RespClient {
        val password = LuaRedisCredentialStore.getPassword(connection.id)
        return RespClient.open(connection.toEndpoint(password))
    }
}
