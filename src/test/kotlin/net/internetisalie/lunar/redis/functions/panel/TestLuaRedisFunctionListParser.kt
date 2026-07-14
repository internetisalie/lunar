package net.internetisalie.lunar.redis.functions.panel

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.redis.functions.DriftStatus
import net.internetisalie.lunar.redis.functions.LuaRedisFunctionDrift
import net.internetisalie.lunar.redis.functions.LuaRedisFunctionListParser
import net.internetisalie.lunar.redis.functions.LuaRedisFunctionsController
import net.internetisalie.lunar.redis.functions.RedisFunctionEntry
import net.internetisalie.lunar.redis.functions.RedisLibraryEntry
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespValue
import net.internetisalie.lunar.redis.run.ScriptedRespServer
import kotlinx.coroutines.runBlocking

/**
 * Unit tests for [LuaRedisFunctionListParser], [LuaRedisFunctionDrift], and
 * [LuaRedisFunctionsController] (TC-PANEL-1, TC-PANEL-2, TC-PANEL-3, TC-DRIFT-1).
 *
 * TC-PANEL-2 and TC-PANEL-3 use [ScriptedRespServer] + the controller's `*WithClient` testability
 * seam to assert the correct RESP commands are sent, mirroring [TestLuaRedisFunctionExecutor].
 */
class TestLuaRedisFunctionListParser : BasePlatformTestCase() {

    private val servers = mutableListOf<ScriptedRespServer>()

    override fun tearDown() {
        try {
            servers.forEach { it.close() }
        } finally {
            super.tearDown()
        }
    }

    // -----------------------------------------------------------------------
    // TC-PANEL-1: RESP3 Map wire shape
    // -----------------------------------------------------------------------

    /**
     * TC-PANEL-1: parse a RESP3 `%map` reply for library `mylib` with function `f` and flag `no-writes`.
     *
     * Wire shape:
     *   Array([ Map([ library_name→"mylib", functions→Array([ Map([name→"f", flags→Array(["no-writes"])]) ]) ]) ])
     */
    fun testParseResp3Map_TC_PANEL_1() {
        val flagsArray = RespValue.Array(listOf(bulk("no-writes")))
        val fnMap = RespValue.Map(
            listOf(
                bulk("name") to bulk("f"),
                bulk("flags") to flagsArray,
            ),
        )
        val libMap = RespValue.Map(
            listOf(
                bulk("library_name") to bulk("mylib"),
                bulk("functions") to RespValue.Array(listOf(fnMap)),
            ),
        )
        val reply = RespValue.Array(listOf(libMap))

        val result = LuaRedisFunctionListParser.parse(reply)

        assertEquals(1, result.size)
        val lib = result[0]
        assertEquals("mylib", lib.name)
        assertNull(lib.libraryCode)
        assertEquals(1, lib.functions.size)
        val fn = lib.functions[0]
        assertEquals("f", fn.name)
        assertEquals(setOf("no-writes"), fn.flags)
    }

    /**
     * TC-PANEL-1 variant: RESP2 array-of-pairs wire shape produces the same model.
     *
     * Wire shape: Array([ Array([ "library_name","mylib","functions",Array([Array(["name","f","flags",Array(["no-writes"])])]) ]) ])
     */
    fun testParseResp2ArrayOfPairs_TC_PANEL_1() {
        val flagsArray = RespValue.Array(listOf(bulk("no-writes")))
        val fnArrayPairs = RespValue.Array(
            listOf(
                bulk("name"), bulk("f"),
                bulk("flags"), flagsArray,
            ),
        )
        val libArrayPairs = RespValue.Array(
            listOf(
                bulk("library_name"), bulk("mylib"),
                bulk("functions"), RespValue.Array(listOf(fnArrayPairs)),
            ),
        )
        val reply = RespValue.Array(listOf(libArrayPairs))

        val result = LuaRedisFunctionListParser.parse(reply)

        assertEquals(1, result.size)
        val lib = result[0]
        assertEquals("mylib", lib.name)
        assertEquals(1, lib.functions.size)
        assertEquals("f", lib.functions[0].name)
        assertEquals(setOf("no-writes"), lib.functions[0].flags)
    }

    /** Parsing a non-Array reply returns an empty list (defensive, §3.8). */
    fun testParseNonArrayReturnsEmpty() {
        val result = LuaRedisFunctionListParser.parse(RespValue.Simple("OK"))
        assertTrue("non-array → empty", result.isEmpty())
    }

    /** A library entry missing `library_name` is silently skipped (no NPE). */
    fun testParseMissingLibraryNameSkipped() {
        val libMap = RespValue.Map(listOf(bulk("engine") to bulk("LUA")))
        val reply = RespValue.Array(listOf(libMap))
        val result = LuaRedisFunctionListParser.parse(reply)
        assertTrue("missing library_name → skipped", result.isEmpty())
    }

    /** WITHCODE: `library_code` is captured when present. */
    fun testParseLibraryCodeCaptured() {
        val code = "#!lua name=mylib\nredis.register_function('f', function() end)"
        val libMap = RespValue.Map(
            listOf(
                bulk("library_name") to bulk("mylib"),
                bulk("library_code") to bulk(code),
                bulk("functions") to RespValue.Array(emptyList()),
            ),
        )
        val reply = RespValue.Array(listOf(libMap))
        val result = LuaRedisFunctionListParser.parse(reply)

        assertEquals(1, result.size)
        assertEquals(code, result[0].libraryCode)
    }

    // -----------------------------------------------------------------------
    // TC-DRIFT-1
    // -----------------------------------------------------------------------

    /** TC-DRIFT-1: identical normalized content → IN_SYNC. */
    fun testDriftInSync_TC_DRIFT_1() {
        val body = "#!lua name=mylib\nX"
        assertEquals(DriftStatus.IN_SYNC, LuaRedisFunctionDrift.compare(body, body))
    }

    /** TC-DRIFT-1: differing content → DRIFTED. */
    fun testDriftDrifted_TC_DRIFT_1() {
        val server = "#!lua name=mylib\nX"
        val local = "#!lua name=mylib\nY"
        assertEquals(DriftStatus.DRIFTED, LuaRedisFunctionDrift.compare(server, local))
    }

    /** TC-DRIFT-1: null serverCode → UNKNOWN. */
    fun testDriftUnknown_TC_DRIFT_1() {
        assertEquals(DriftStatus.UNKNOWN, LuaRedisFunctionDrift.compare(null, "anything"))
    }

    /** CRLF normalization: `\r\n` vs `\n` on the same content still → IN_SYNC. */
    fun testDriftNormalizesLineEndings() {
        val server = "#!lua name=lib\r\nX"
        val local = "#!lua name=lib\nX"
        assertEquals(DriftStatus.IN_SYNC, LuaRedisFunctionDrift.compare(server, local))
    }

    /** Trailing whitespace normalization: trailing spaces/newlines ignored. */
    fun testDriftNormalizesTrailingWhitespace() {
        val server = "#!lua name=lib\nX\n\n"
        val local = "#!lua name=lib\nX"
        assertEquals(DriftStatus.IN_SYNC, LuaRedisFunctionDrift.compare(server, local))
    }

    // -----------------------------------------------------------------------
    // TC-PANEL-2: controller delete sends FUNCTION DELETE <name>
    // -----------------------------------------------------------------------

    /**
     * TC-PANEL-2: [LuaRedisFunctionsController.deleteWithClient] sends `FUNCTION DELETE mylib`
     * over the injected client; on `Simple("OK")` the model row should be removed (contract).
     */
    fun testControllerDelete_TC_PANEL_2() {
        val server = scriptedServer(simpleReply("OK"))
        val reply = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                LuaRedisFunctionsController().deleteWithClient(client, "mylib")
            } finally {
                client.dispose()
            }
        }

        assertEquals(RespValue.Simple("OK"), reply)
        val cmds = server.requests
        assertEquals("HELLO + FUNCTION DELETE", 2, cmds.size)
        assertTrue("FUNCTION DELETE in command", cmds[1].contains("FUNCTION") && cmds[1].contains("DELETE"))
        assertTrue("library name in command", cmds[1].contains("mylib"))
    }

    // -----------------------------------------------------------------------
    // TC-PANEL-3: controller deploy sends FUNCTION LOAD REPLACE then list
    // -----------------------------------------------------------------------

    /**
     * TC-PANEL-3: [LuaRedisFunctionsController.deployWithClient] sends `FUNCTION LOAD REPLACE <body>`;
     * on success the caller should refresh (asserted by verifying no extra FUNCTION LIST here —
     * the controller method returns after LOAD, and the panel calls refresh()).
     */
    fun testControllerDeploy_TC_PANEL_3() {
        val fileBody = "#!lua name=mylib\nredis.register_function('f', function(keys,args) return 1 end)"
        val server = scriptedServer(simpleReply("mylib"))
        val reply = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                LuaRedisFunctionsController().deployWithClient(client, fileBody)
            } finally {
                client.dispose()
            }
        }

        assertEquals(RespValue.Simple("mylib"), reply)
        val cmds = server.requests
        assertEquals("HELLO + FUNCTION LOAD REPLACE", 2, cmds.size)
        val loadCmd = cmds[1]
        assertTrue("FUNCTION in command", loadCmd.contains("FUNCTION"))
        assertTrue("LOAD in command", loadCmd.contains("LOAD"))
        assertTrue("REPLACE in command", loadCmd.contains("REPLACE"))
        assertTrue("file body in command", loadCmd.contains("mylib"))
    }

    /**
     * TC-PANEL-1 + controller: [LuaRedisFunctionsController.listWithClient] sends
     * `FUNCTION LIST WITHCODE` and returns parsed entries.
     */
    fun testControllerListWithCode_TC_PANEL_1() {
        val code = "#!lua name=mylib\nredis.register_function('f', function() end)"
        val resp3FnMap = RespValue.Map(
            listOf(
                bulk("name") to bulk("f"),
                bulk("flags") to RespValue.Array(emptyList()),
            ),
        )
        val resp3LibMap = RespValue.Map(
            listOf(
                bulk("library_name") to bulk("mylib"),
                bulk("library_code") to bulk(code),
                bulk("functions") to RespValue.Array(listOf(resp3FnMap)),
            ),
        )
        val replyBytes = encodeResp3Array(listOf(resp3LibMap))
        val server = scriptedServer(replyBytes)

        val entries = runBlocking {
            val client = RespClient.open(server.endpoint())
            try {
                LuaRedisFunctionsController().listWithClient(client, withCode = true)
            } finally {
                client.dispose()
            }
        }

        // Verify command was FUNCTION LIST WITHCODE
        val cmds = server.requests
        assertEquals("HELLO + FUNCTION LIST WITHCODE", 2, cmds.size)
        assertTrue("FUNCTION in cmd", cmds[1].contains("FUNCTION"))
        assertTrue("LIST in cmd", cmds[1].contains("LIST"))
        assertTrue("WITHCODE in cmd", cmds[1].contains("WITHCODE"))

        // Verify parsed result
        assertEquals(1, entries.size)
        assertEquals("mylib", entries[0].name)
        assertEquals(code, entries[0].libraryCode)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun bulk(text: String): RespValue.Bulk = RespValue.Bulk(text.toByteArray(Charsets.UTF_8))

    private fun simpleReply(text: String): ByteArray = "+$text\r\n".toByteArray(Charsets.UTF_8)

    private fun scriptedServer(vararg executorReplies: ByteArray): ScriptedRespServer {
        val replies = mutableListOf(ScriptedRespServer.HELLO_MAP_REPLY)
        replies.addAll(executorReplies)
        val server = ScriptedRespServer(replies)
        servers.add(server)
        return server
    }

    /**
     * Encodes a minimal RESP3 array reply for [LuaRedisFunctionsController.listWithClient] tests.
     *
     * Only covers the subset needed: an `*N\r\n` outer array wrapping `%N\r\n` RESP3 maps with
     * `$N\r\n` bulk-string keys and values. Handles nested `*N\r\n` arrays for `functions` and
     * `flags`. Sufficient to round-trip through [LuaRedisFunctionListParser] in tests.
     */
    private fun encodeResp3Array(items: List<RespValue>): ByteArray {
        val sb = StringBuilder()
        sb.append("*${items.size}\r\n")
        for (item in items) {
            appendRespValue(sb, item)
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun appendRespValue(sb: StringBuilder, v: RespValue) {
        when (v) {
            is RespValue.Map -> {
                sb.append("%${v.entries.size}\r\n")
                for ((k, value) in v.entries) {
                    appendRespValue(sb, k)
                    appendRespValue(sb, value)
                }
            }
            is RespValue.Array -> {
                val items = v.items ?: emptyList()
                sb.append("*${items.size}\r\n")
                for (item in items) {
                    appendRespValue(sb, item)
                }
            }
            is RespValue.Bulk -> {
                val bytes = v.bytes
                if (bytes == null) {
                    sb.append("\$-1\r\n")
                } else {
                    val text = bytes.toString(Charsets.UTF_8)
                    sb.append("\$${bytes.size}\r\n$text\r\n")
                }
            }
            is RespValue.Simple -> sb.append("+${v.text}\r\n")
            is RespValue.Error -> sb.append("-${v.klass} ${v.message}\r\n")
            else -> sb.append("+\r\n")
        }
    }
}
