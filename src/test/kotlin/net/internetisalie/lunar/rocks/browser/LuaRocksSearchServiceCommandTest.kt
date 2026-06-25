package net.internetisalie.lunar.rocks.browser

import net.internetisalie.lunar.rocks.LuaRocksEnvironment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TC 1, TC 2: verifies that [LuaRocksEnvironment.withServer] produces the correct argument
 * shape for the search and list sub-commands.
 *
 * These are structural (command-shape) tests — no process is spawned. The full service
 * integration (TC 1: project with resolved server produces --server in command) is verified
 * in the human-verification checklist scenario 2.1.
 */
class LuaRocksSearchServiceCommandTest {

    /**
     * TC 1 (partial): when a server is resolved, the search sub-command args contain
     * ["--server", url] *before* "search".
     */
    @Test
    fun searchArgsContainServerWhenResolved() {
        val server = "http://localhost:8080"
        val subArgs = LuaRocksEnvironment.withServer(listOf("search", "--porcelain", "inspect"), server)
        assertTrue(subArgs.contains("--server"), "args must contain --server token")
        assertTrue(subArgs.contains(server), "args must contain the server URL")
        val serverIdx = subArgs.indexOf("--server")
        assertEquals(server, subArgs[serverIdx + 1], "URL must follow --server immediately")
        assertEquals("search", subArgs[serverIdx + 2], "--server pair must appear before subcommand")
    }

    /**
     * TC 2 (regression guard): when no server is resolved (null), the argument list must
     * contain no --server token and must be identical to the pre-ROCKS-06 command.
     */
    @Test
    fun searchArgsNoServerWhenNull() {
        val subArgs = LuaRocksEnvironment.withServer(listOf("search", "--porcelain", "inspect"), null)
        assertFalse(subArgs.contains("--server"), "args must NOT contain --server when server is null")
        assertEquals(listOf("search", "--porcelain", "inspect"), subArgs)
    }

    /**
     * TC 1 (partial): same contract for the list/installed sub-command.
     */
    @Test
    fun installedArgsContainServerWhenResolved() {
        val server = "https://reg.example"
        val subArgs = LuaRocksEnvironment.withServer(listOf("list", "--porcelain"), server)
        assertTrue(subArgs.contains("--server"))
        assertTrue(subArgs.contains(server))
        val serverIdx = subArgs.indexOf("--server")
        assertEquals("list", subArgs[serverIdx + 2])
    }

    /**
     * TC 2 (regression guard): list args unchanged when no server.
     */
    @Test
    fun installedArgsNoServerWhenNull() {
        val subArgs = LuaRocksEnvironment.withServer(listOf("list", "--porcelain"), null)
        assertFalse(subArgs.contains("--server"))
        assertEquals(listOf("list", "--porcelain"), subArgs)
    }
}
