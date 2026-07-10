package net.internetisalie.lunar.redis.run

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespException
import net.internetisalie.lunar.redis.resp.RespValue
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Unit coverage of [LuaRedisScriptExecutor] against a scripted in-process RESP server (design §3.8).
 *
 * Covers the command-selection table, TC-SHA-1 (`SCRIPT LOAD` + `NOSCRIPT` single-retry), and TC-RO-1
 * (read-only Redis-<7 version gate fails fast, no `_RO` sent). No live server: a
 * [ScriptedRespServer] pops one ordered reply per inbound frame, and the client's no-auth `HELLO 3`
 * handshake consumes the first reply. Each test uses a fresh connection id so the process-wide
 * [LuaRedisScriptShaCache] singleton never carries state across tests.
 */
class TestLuaRedisScriptExecutor : BasePlatformTestCase() {

    private val servers = mutableListOf<ScriptedRespServer>()

    override fun tearDown() {
        try {
            servers.forEach { it.close() }
        } finally {
            super.tearDown()
        }
    }

    /** Command-selection table (design §3.8): EVAL/EVALSHA × read-only picks the right command name. */
    fun testCommandSelectionTable() {
        assertEquals("EVAL", LuaRedisScriptExecutor.evalCommand(readOnly = false))
        assertEquals("EVAL_RO", LuaRedisScriptExecutor.evalCommand(readOnly = true))
        assertEquals("EVALSHA", LuaRedisScriptExecutor.evalShaCommand(readOnly = false))
        assertEquals("EVALSHA_RO", LuaRedisScriptExecutor.evalShaCommand(readOnly = true))
    }

    /** The SHA1 is the lowercase hex digest the server would compute from the same body. */
    fun testSha1HexMatchesServerAlgorithm() {
        val jdk = java.security.MessageDigest.getInstance("SHA-1")
            .digest("return 1".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals(jdk, LuaRedisScriptExecutor.sha1Hex("return 1"))
    }

    /** TC-SHA-1: EVALSHA with an empty cache loads, and a `NOSCRIPT` triggers one re-LOAD + retry that succeeds. */
    fun testEvalShaNoScriptRetrySucceeds() {
        val connectionId = UUID.randomUUID().toString()
        val server = scriptedServer(
            bulkReply("shaOfBody"),
            errorReply("NOSCRIPT No matching script"),
            bulkReply("shaOfBody"),
            "+OK\r\n".toByteArray(Charsets.UTF_8),
        )
        val context = context(connectionId, LuaRedisExecMode.EVALSHA, readOnly = false)

        val reply = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                LuaRedisScriptExecutor().execute(client, context, "return 1")
            } finally {
                client.dispose()
            }
        }

        assertEquals(RespValue.Simple("OK"), reply)
        val commands = server.requests
        assertEquals("expected: HELLO, SCRIPT LOAD, EVALSHA, SCRIPT LOAD, EVALSHA — got $commands", 5, commands.size)
        assertTrue("first EVALSHA present", commands[2].contains("EVALSHA"))
        assertTrue("re-LOAD after NOSCRIPT", commands[3].contains("SCRIPT") && commands[3].contains("LOAD"))
    }

    /** A second consecutive `NOSCRIPT` surfaces the error rather than looping (design §3.8). */
    fun testEvalShaSecondNoScriptSurfaces() {
        val connectionId = UUID.randomUUID().toString()
        val server = scriptedServer(
            bulkReply("shaOfBody"),
            errorReply("NOSCRIPT first"),
            bulkReply("shaOfBody"),
            errorReply("NOSCRIPT second"),
        )
        val context = context(connectionId, LuaRedisExecMode.EVALSHA, readOnly = false)

        val reply = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                LuaRedisScriptExecutor().execute(client, context, "return 1")
            } finally {
                client.dispose()
            }
        }

        assertTrue("second NOSCRIPT surfaces as an Error reply, got $reply", reply is RespValue.Error)
        assertEquals("NOSCRIPT", (reply as RespValue.Error).klass)
    }

    /** TC-RO-1: read-only against a Redis 6.2 server fails fast with ServerVersion; no `_RO` command sent. */
    fun testReadOnlyVersionGateFailsFastBelow7() {
        val connectionId = UUID.randomUUID().toString()
        val server = scriptedServer(
            bulkReply("# Server\r\nredis_version:6.2.0\r\nredis_mode:standalone\r\n"),
        )
        val context = context(connectionId, LuaRedisExecMode.EVAL, readOnly = true)

        val failure = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                runCatching { LuaRedisScriptExecutor().execute(client, context, "return 1") }.exceptionOrNull()
            } finally {
                client.dispose()
            }
        }

        assertTrue("expected RespException.ServerVersion, got $failure", failure is RespException.ServerVersion)
        assertTrue(
            "message names the required version",
            failure?.message?.contains(LuaRedisScriptExecutor.READ_ONLY_FLOOR) == true,
        )
        val commands = server.requests
        assertTrue("no EVAL_RO/EVALSHA_RO sent after gate: $commands", commands.none { it.contains("_RO") })
    }

    /** Read-only against a Redis 8 server passes the gate and issues the `_RO` variant. */
    fun testReadOnlyVersionGatePassesAtOrAbove7() {
        val connectionId = UUID.randomUUID().toString()
        val server = scriptedServer(
            bulkReply("# Server\r\nredis_version:8.0.0\r\n"),
            ":42\r\n".toByteArray(Charsets.UTF_8),
        )
        val context = context(connectionId, LuaRedisExecMode.EVAL, readOnly = true)

        val reply = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                LuaRedisScriptExecutor().execute(client, context, "return 42")
            } finally {
                client.dispose()
            }
        }

        assertEquals(RespValue.Integer(42), reply)
        assertTrue("EVAL_RO sent", server.requests.any { it.contains("EVAL_RO") })
    }

    private fun context(connectionId: String, mode: LuaRedisExecMode, readOnly: Boolean): LuaRedisExecContext =
        LuaRedisExecContext(
            connectionId = connectionId,
            execMode = mode,
            readOnly = readOnly,
            keys = listOf("k1"),
            argv = listOf("v1"),
        )

    private fun scriptedServer(vararg executorReplies: ByteArray): ScriptedRespServer {
        val replies = mutableListOf(ScriptedRespServer.HELLO_MAP_REPLY)
        replies.addAll(executorReplies)
        val server = ScriptedRespServer(replies)
        servers.add(server)
        return server
    }

    private fun bulkReply(payload: String): ByteArray {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        return "\$${bytes.size}\r\n".toByteArray(Charsets.UTF_8) + bytes + "\r\n".toByteArray(Charsets.UTF_8)
    }

    private fun errorReply(text: String): ByteArray = "-$text\r\n".toByteArray(Charsets.UTF_8)
}
