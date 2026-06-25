package net.internetisalie.lunar.rocks.publish

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TC-ROCKS-08-04 + TC 5, TC 6: `luarocks upload` command-line assembly (design §4, §2.5). */
class RockUploadCommandTest {

    // ── Pre-ROCKS-06 baseline (regression guards) ────────────────────────────

    @Test
    fun basicUploadArguments() {
        assertEquals(
            listOf("upload", "app-1.rockspec", "--api-key=SECRET"),
            RockUploadCommand.arguments("app-1.rockspec", "SECRET"),
        )
    }

    @Test
    fun forceAppendsFlag() {
        assertEquals(
            listOf("upload", "app-1.rockspec", "--api-key=SECRET", "--force"),
            RockUploadCommand.arguments("app-1.rockspec", "SECRET", force = true),
        )
    }

    @Test
    fun apiKeyIsASingleToken() {
        val args = RockUploadCommand.arguments("a.rockspec", "key with spaces")
        // key rides --api-key= as a single token — find it in args (may be offset by --server tokens)
        val keyToken = args.firstOrNull { it.startsWith("--api-key=") }
        assertEquals("--api-key=key with spaces", keyToken)
    }

    @Test
    fun buildUsesExecutableAsExePath() {
        val command = RockUploadCommand.build("/usr/bin/luarocks", "a.rockspec", "K")
        assertEquals("/usr/bin/luarocks", command.exePath)
        assertEquals(
            listOf("upload", "a.rockspec", "--api-key=K"),
            command.parametersList.list,
        )
    }

    // ── TC 5: server non-null → --server injected before "upload" ────────────

    @Test
    fun argumentsWithServerPrependsServerBeforeUpload() {
        val args = RockUploadCommand.arguments("foo-1.0.rockspec", "K", server = "http://localhost:8080")
        assertTrue(args.contains("--server"), "args must contain --server when server is set (TC 5)")
        assertTrue(args.contains("http://localhost:8080"), "args must contain the server URL (TC 5)")
        val serverIdx = args.indexOf("--server")
        assertEquals("http://localhost:8080", args[serverIdx + 1], "URL must follow --server")
        assertEquals("upload", args[serverIdx + 2], "--server must appear before the upload subcommand")
    }

    @Test
    fun argumentsWithServerContainsApiKey() {
        val args = RockUploadCommand.arguments("foo-1.0.rockspec", "K", server = "http://localhost:8080")
        val keyToken = args.firstOrNull { it.startsWith("--api-key=") }
        assertEquals("--api-key=K", keyToken, "api-key must still be present when server is set (TC 5)")
    }

    @Test
    fun buildWithServerPassesServerInParameters() {
        val command = RockUploadCommand.build("/usr/bin/luarocks", "foo.rockspec", "K", server = "http://localhost:8080")
        val params = command.parametersList.list
        assertTrue(params.contains("--server"))
        assertTrue(params.contains("http://localhost:8080"))
        val sIdx = params.indexOf("--server")
        assertEquals("upload", params[sIdx + 2])
    }

    // ── TC 6: server null → no --server token (regression guard) ─────────────

    @Test
    fun argumentsNullServerNoServerToken() {
        val args = RockUploadCommand.arguments("foo-1.0.rockspec", "K", server = null)
        assertFalse(args.contains("--server"), "args must NOT contain --server when server is null (TC 6)")
        assertEquals(listOf("upload", "foo-1.0.rockspec", "--api-key=K"), args)
    }

    @Test
    fun buildNullServerParametersUnchanged() {
        val command = RockUploadCommand.build("/usr/bin/luarocks", "a.rockspec", "K", server = null)
        val params = command.parametersList.list
        assertFalse(params.contains("--server"), "no --server in params when server is null (TC 6)")
        assertEquals(listOf("upload", "a.rockspec", "--api-key=K"), params)
    }
}
