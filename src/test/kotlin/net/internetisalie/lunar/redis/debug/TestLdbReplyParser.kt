package net.internetisalie.lunar.redis.debug

import net.internetisalie.lunar.redis.resp.RespValue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Socket-free coverage of [LdbReplyParser.parse] (design §3.3).
 *
 * Covers TC-LDB-DEC-1 (step stop), TC-LDB-DEC-2 (session-end / fork-timeout),
 * TC-LDB-DEC-3 (compile error). Blocks are RESP arrays of bulk status lines (design §4.1).
 */
class TestLdbReplyParser {

    private fun block(vararg lines: String): RespValue.Array =
        RespValue.Array(lines.map { RespValue.Bulk(it.toByteArray(Charsets.UTF_8)) })

    /** TC-LDB-DEC-1: a step stop yields the 1-based server line + gutter-stripped source. */
    @Test
    fun testParseStepStop() {
        val reply = block("* Stopped at 3, stop reason = step", "3   local x = 1")
        assertEquals(
            LdbEvent.Stop(serverLine = 3, reason = StopReason.STEP, sourceLine = "local x = 1"),
            LdbReplyParser.parse(reply),
        )
    }

    /** TC-LDB-DEC-2: normal session-end line. */
    @Test
    fun testParseSessionEnded() {
        assertEquals(
            LdbEvent.SessionEnded(EndReason.ENDED),
            LdbReplyParser.parse(block("* Lua debugging session ended")),
        )
    }

    /** TC-LDB-DEC-2: the forked-timeout variant is matched by its `Forked` prefix. */
    @Test
    fun testParseForkedSessionTimeout() {
        assertEquals(
            LdbEvent.SessionEnded(EndReason.FORK_TIMEOUT),
            LdbReplyParser.parse(block("* Forked debugging session was closed")),
        )
    }

    /** TC-LDB-DEC-3: a compile error extracts the message and the `user_script:<N>` position. */
    @Test
    fun testParseCompileError() {
        val reply = block("* Error compiling script (new function): user_script:2: '=' expected near 'x'")
        assertEquals(
            LdbEvent.Error(
                kind = LdbErrorKind.COMPILE,
                message = "user_script:2: '=' expected near 'x'",
                scriptLine = 2,
            ),
            LdbReplyParser.parse(reply),
        )
    }

    /** A breakpoint stop (no `stop reason`) defaults to BREAKPOINT. */
    @Test
    fun testParseBreakpointStopDefault() {
        val reply = block("* Stopped at 7", "7   return sum")
        assertEquals(
            LdbEvent.Stop(serverLine = 7, reason = StopReason.BREAKPOINT, sourceLine = "return sum"),
            LdbReplyParser.parse(reply),
        )
    }

    /** An unrecognized block degrades to Ack — never a throw (contract §1, Risk 2.1). */
    @Test
    fun testParseUnrecognizedBlockIsAck() {
        assertEquals(LdbEvent.Ack, LdbReplyParser.parse(block("+OK")))
        assertEquals(LdbEvent.Ack, LdbReplyParser.parse(RespValue.Array(emptyList())))
    }

    /** A transport-level RESP error surfaces as a RUNTIME in-band error with the script line. */
    @Test
    fun testParseRespErrorReply() {
        val reply = RespValue.Error("ERR", "user_script:4: attempt to index a nil value")
        val event = LdbReplyParser.parse(reply)
        assertEquals(
            LdbEvent.Error(
                kind = LdbErrorKind.RUNTIME,
                message = "ERR user_script:4: attempt to index a nil value",
                scriptLine = 4,
            ),
            event,
        )
    }
}
