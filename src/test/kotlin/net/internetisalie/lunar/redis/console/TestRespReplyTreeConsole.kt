package net.internetisalie.lunar.redis.console

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.redis.resp.RespValue

/**
 * Error-class display coverage for [RespReplyTreeConsole] (design §3.4, §2.6, TC-CON-2).
 *
 * TC-CON-2: `showError(RespValue.Error("WRONGTYPE", …))` renders the `WRONGTYPE` class tag. The
 * formatting is asserted via the pure [RespReplyTreeConsole.formatError] (deferred console text is
 * fragile to read in a headless test); a light-fixture smoke test then drives the live `showError`
 * and `showReply` paths to confirm the console builds and renders without throwing.
 */
class TestRespReplyTreeConsole : BasePlatformTestCase() {

    /** TC-CON-2: the error line carries the server error **class** tag. */
    fun testErrorLineShowsClassTag() {
        val error = RespValue.Error(
            "WRONGTYPE",
            "Operation against a key holding the wrong kind of value",
        )
        val line = RespReplyTreeConsole.formatError(error)

        assertTrue("line must carry the WRONGTYPE class tag: $line", line.contains("WRONGTYPE"))
        assertEquals(
            "(error) WRONGTYPE Operation against a key holding the wrong kind of value",
            line,
        )
    }

    /** A bare error class (no message) still renders the class tag without a trailing space. */
    fun testErrorLineWithoutMessage() {
        assertEquals("(error) ERR", RespReplyTreeConsole.formatError(RespValue.Error("ERR", "")))
    }

    /** Smoke: the live console builds and renders scalar/error/array replies without throwing (design §2.6). */
    fun testConsoleRendersLive() {
        val console = RespReplyTreeConsole(project)
        try {
            console.showError(RespValue.Error("WRONGTYPE", "bad"))
            console.showReply(RespValue.Simple("OK"))
            console.showReply(
                RespValue.Array(listOf(RespValue.Integer(1), RespValue.Integer(2))),
            )
            assertNotNull(console.component)
        } finally {
            Disposer.dispose(console)
        }
    }
}
