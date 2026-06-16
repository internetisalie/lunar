package net.internetisalie.lunar.tool.health

import net.internetisalie.lunar.tool.LuaTool
import net.internetisalie.lunar.tool.LuaToolType
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [LuaToolHealthChecker] — the no-subprocess branches (missing binary, non-executable,
 * the mtime short-circuit) and [LuaToolHealthChecker.applyResult] write-back. These are hermetic:
 * they never spawn the real `--version` subprocess, so they need no IDE fixture.
 */
class LuaToolHealthCheckerTest {

    private fun tool(path: String): LuaTool =
        LuaTool(type = LuaToolType.LUACHECK, name = "luacheck", path = path)

    @Test
    fun `missing binary reports invalid`() {
        val result = LuaToolHealthChecker.check(tool("/no/such/binary-xyz"))
        assertFalse(result.isValid)
        assertEquals("Binary missing", result.reason)
    }

    @Test
    fun `non-executable file reports permission denied`() {
        val file = File.createTempFile("lunar-tool", ".bin")
        file.deleteOnExit()
        file.setExecutable(false)
        // Skip on platforms where executability cannot be cleared (the assertion would be vacuous).
        if (file.canExecute()) return

        val result = LuaToolHealthChecker.check(tool(file.absolutePath))
        assertFalse(result.isValid)
        assertEquals("Permission denied", result.reason)
    }

    @Test
    fun `unchanged mtime short-circuits the slow check`() {
        val file = File.createTempFile("lunar-tool", ".bin")
        file.deleteOnExit()
        check(file.setExecutable(true)) { "test requires an executable temp file" }

        val cached = tool(file.absolutePath).apply {
            isValid = true
            version = "0.26.0"
            lastCheckedMtime = file.lastModified()
        }

        // The temp file is not a runnable program; if the gate failed and we ran it, the result
        // would NOT be the cached "OK 0.26.0". Getting the cached value proves the short-circuit.
        val result = LuaToolHealthChecker.check(cached)
        assertTrue(result.isValid)
        assertEquals("0.26.0", result.version)
        assertEquals("OK 0.26.0", result.reason)
    }

    @Test
    fun `applyResult writes back status fields`() {
        val file = File.createTempFile("lunar-tool", ".bin")
        file.deleteOnExit()
        val subject = tool(file.absolutePath)

        LuaToolHealthChecker.applyResult(subject, HealthResult(isValid = true, version = "1.1.0", reason = "OK 1.1.0"))

        assertTrue(subject.isValid)
        assertEquals("1.1.0", subject.version)
        assertEquals("OK 1.1.0", subject.lastCheckReason)
        assertEquals(file.lastModified(), subject.lastCheckedMtime)
    }
}
