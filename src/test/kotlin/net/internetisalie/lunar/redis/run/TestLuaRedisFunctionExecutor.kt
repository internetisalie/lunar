package net.internetisalie.lunar.redis.run

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.redis.connection.LuaRedisConnectionSettings
import net.internetisalie.lunar.redis.connection.LuaRedisProvisioning
import net.internetisalie.lunar.redis.connection.LuaRedisServerConnection
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespValue
import kotlinx.coroutines.runBlocking

/**
 * Unit coverage of [LuaRedisFunctionExecutor] against a scripted in-process RESP server.
 *
 * Mirrors [TestLuaRedisScriptExecutor]: a [ScriptedRespServer] pops one ordered reply per
 * inbound frame; the client's no-auth `HELLO 3` handshake consumes the first reply. Covers
 * TC-DEPLOY-1, TC-CALL-1, TC-RO-1 from requirements.md.
 */
class TestLuaRedisFunctionExecutor : BasePlatformTestCase() {

    private val servers = mutableListOf<ScriptedRespServer>()

    override fun tearDown() {
        try {
            servers.forEach { it.close() }
        } finally {
            super.tearDown()
        }
    }

    /**
     * TC-DEPLOY-1: deployOnly=true → exactly one FUNCTION LOAD [REPLACE] <body>, no FCALL sent.
     * The loadReply (+lib) is returned directly.
     */
    fun testDeployOnly_TC_DEPLOY_1() {
        val server = scriptedServer(simpleReply("lib"))
        val config = newConfig(deployOnly = true, replaceOnLoad = true)

        val reply = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                LuaRedisFunctionExecutor().execute(client, config, LIBRARY_BODY)
            } finally {
                client.dispose()
            }
        }

        assertEquals(RespValue.Simple("lib"), reply)
        val cmds = server.requests
        assertEquals("expected HELLO then FUNCTION LOAD", 2, cmds.size)
        assertTrue("FUNCTION LOAD REPLACE in request", cmds[1].contains("FUNCTION") && cmds[1].contains("LOAD") && cmds[1].contains("REPLACE"))
        assertFalse("no FCALL sent for deploy-only", cmds.any { it.contains("FCALL") })
    }

    /**
     * TC-DEPLOY-1 without REPLACE: deployOnly=true, replaceOnLoad=false → FUNCTION LOAD without REPLACE.
     */
    fun testDeployOnlyWithoutReplace() {
        val server = scriptedServer(simpleReply("lib"))
        val config = newConfig(deployOnly = true, replaceOnLoad = false)

        runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                LuaRedisFunctionExecutor().execute(client, config, LIBRARY_BODY)
            } finally {
                client.dispose()
            }
        }

        val loadCmd = server.requests[1]
        assertTrue("FUNCTION LOAD present", loadCmd.contains("FUNCTION") && loadCmd.contains("LOAD"))
        assertFalse("REPLACE must not appear", loadCmd.contains("REPLACE"))
    }

    /**
     * TC-CALL-1: FCALL mode, deployOnly=false, replaceOnLoad=true, functionName="f",
     * keys=["k1"], argv=["a1"] → LOAD then FCALL f 1 k1 a1.
     */
    fun testFcallDeployAndCall_TC_CALL_1() {
        val server = scriptedServer(
            simpleReply("lib"),
            bulkReply("result"),
        )
        val config = newConfig(
            deployOnly = false,
            replaceOnLoad = true,
            functionName = "f",
            keys = listOf("k1"),
            argv = listOf("a1"),
        )

        val reply = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                LuaRedisFunctionExecutor().execute(client, config, LIBRARY_BODY)
            } finally {
                client.dispose()
            }
        }

        assertEquals(RespValue.Bulk("result".toByteArray(Charsets.UTF_8)), reply)
        val cmds = server.requests
        assertEquals("HELLO + LOAD + FCALL", 3, cmds.size)
        val fcallCmd = cmds[2]
        assertTrue("FCALL in invocation", fcallCmd.contains("FCALL"))
        assertFalse("not FCALL_RO for readOnly=false", fcallCmd.contains("FCALL_RO"))
        assertTrue("function name 'f' in FCALL", fcallCmd.contains("f"))
        assertTrue("numkeys '1' in FCALL", fcallCmd.contains("1"))
        assertTrue("key 'k1' in FCALL", fcallCmd.contains("k1"))
        assertTrue("argv 'a1' in FCALL", fcallCmd.contains("a1"))
    }

    /**
     * TC-RO-1: readOnly=true → invocation verb is FCALL_RO.
     * A server write-error reply is surfaced verbatim (not blocked client-side).
     */
    fun testReadOnlyUsesFcallRo_TC_RO_1() {
        val server = scriptedServer(
            simpleReply("lib"),
            errorReply("ERR Write commands are not allowed from read-only scripts"),
        )
        val config = newConfig(
            deployOnly = false,
            replaceOnLoad = false,
            readOnly = true,
            functionName = "f",
        )

        val reply = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                LuaRedisFunctionExecutor().execute(client, config, LIBRARY_BODY)
            } finally {
                client.dispose()
            }
        }

        val cmds = server.requests
        assertTrue("FCALL_RO sent for readOnly=true", cmds[2].contains("FCALL_RO"))
        assertTrue("server error surfaced as RespValue.Error", reply is RespValue.Error)
    }

    /**
     * LOAD error is surfaced immediately without attempting FCALL.
     */
    fun testLoadErrorSurfacedWithoutFcall() {
        val server = scriptedServer(
            errorReply("ERR Error loading shared library"),
        )
        val config = newConfig(deployOnly = false, functionName = "f")

        val reply = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                LuaRedisFunctionExecutor().execute(client, config, LIBRARY_BODY)
            } finally {
                client.dispose()
            }
        }

        assertTrue("LOAD error surfaced", reply is RespValue.Error)
        val cmds = server.requests
        assertEquals("only HELLO + LOAD, no FCALL", 2, cmds.size)
        assertFalse("no FCALL after load error", cmds.any { it.contains("FCALL") })
    }

    // -------------------------------------------------------------------------
    // Config factory helpers
    // -------------------------------------------------------------------------

    private fun newConfig(
        deployOnly: Boolean = false,
        replaceOnLoad: Boolean = true,
        readOnly: Boolean = false,
        functionName: String = "f",
        keys: List<String> = emptyList(),
        argv: List<String> = emptyList(),
    ): LuaRedisRunConfiguration {
        seedConnection("u1")
        val type = LuaRedisRunConfigurationType.getInstance()
        val factory = type.configurationFactories[0]
        val config = factory.createTemplateConfiguration(project) as LuaRedisRunConfiguration
        config.scriptPath = "lib.lua"
        config.connectionId = "u1"
        config.execMode = LuaRedisExecMode.FCALL
        config.deployOnly = deployOnly
        config.replaceOnLoad = replaceOnLoad
        config.readOnly = readOnly
        config.functionName = functionName
        config.keys = keys
        config.argv = argv
        return config
    }

    private fun seedConnection(id: String) {
        LuaRedisConnectionSettings.getInstance(project).upsert(
            LuaRedisServerConnection(
                id = id,
                name = "local",
                host = "127.0.0.1",
                port = 6379,
                tls = false,
                database = 0,
                username = null,
                provisioning = LuaRedisProvisioning.Remote,
            ),
        )
    }

    // -------------------------------------------------------------------------
    // Server helpers (mirror TestLuaRedisScriptExecutor)
    // -------------------------------------------------------------------------

    private fun scriptedServer(vararg executorReplies: ByteArray): ScriptedRespServer {
        val replies = mutableListOf(ScriptedRespServer.HELLO_MAP_REPLY)
        replies.addAll(executorReplies)
        val server = ScriptedRespServer(replies)
        servers.add(server)
        return server
    }

    private fun simpleReply(text: String): ByteArray = "+$text\r\n".toByteArray(Charsets.UTF_8)

    private fun bulkReply(payload: String): ByteArray {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        return "\$${bytes.size}\r\n".toByteArray(Charsets.UTF_8) + bytes + "\r\n".toByteArray(Charsets.UTF_8)
    }

    private fun errorReply(text: String): ByteArray = "-$text\r\n".toByteArray(Charsets.UTF_8)

    private companion object {
        const val LIBRARY_BODY = "#!lua name=lib\nredis.register_function('f', function(keys, args) return 1 end)"
    }
}
