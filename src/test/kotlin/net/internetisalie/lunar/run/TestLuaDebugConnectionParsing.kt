package net.internetisalie.lunar.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Socket-free coverage of the parsing/model surface of [LuaDebugConnection] and its command model
 * ([DebugCommand], [DebugCommandKind]). No socket is opened and no DBGp loop is run (MAINT-13-01/-02).
 */
class TestLuaDebugConnectionParsing {

    /** TC 1: breakpoint pause data `<file> <line>` yields file and line groups. */
    @Test
    fun testBreakpointPatternParsesFileAndLine() {
        val matcher = LuaDebugConnection.breakpointDataPattern.matcher("main.lua 42")
        assertTrue(matcher.matches())
        assertEquals("main.lua", matcher.group(1))
        assertEquals("42", matcher.group(2))
    }

    /** TC 2: the greedy `(.+)` absorbs paths containing spaces up to the final numeric group. */
    @Test
    fun testBreakpointPatternAllowsSpacesInPath() {
        val matcher = LuaDebugConnection.breakpointDataPattern.matcher("src/my file.lua 7")
        assertTrue(matcher.matches())
        assertEquals("src/my file.lua", matcher.group(1))
        assertEquals("7", matcher.group(2))
    }

    /** TC 3: data with no trailing line number does not match. */
    @Test
    fun testBreakpointPatternRejectsMissingLine() {
        assertFalse(LuaDebugConnection.breakpointDataPattern.matcher("main.lua").matches())
    }

    /** TC 4: watchpoint pause data `<file> <line> <index>` yields all three groups. */
    @Test
    fun testWatchpointPatternParsesFileLineIndex() {
        val matcher = LuaDebugConnection.watchpointDataPattern.matcher("a.lua 3 5")
        assertTrue(matcher.matches())
        assertEquals("a.lua", matcher.group(1))
        assertEquals("3", matcher.group(2))
        assertEquals("5", matcher.group(3))
    }

    /** TC 5: watchpoint data missing the index does not match. */
    @Test
    fun testWatchpointPatternRejectsMissingIndex() {
        assertFalse(LuaDebugConnection.watchpointDataPattern.matcher("a.lua 3").matches())
    }

    /** TC 6, 7: command serialization upper-cases the kind and space-joins the args. */
    @Test
    fun testCommandToStringSingleAndArgs() {
        assertEquals("OVER", DebugCommand(DebugCommandKind.OVER).toString())
        assertEquals("SETW x>5", DebugCommand(DebugCommandKind.SETW, listOf("x>5")).toString())
    }

    /** TC 8, 9, 10: representative kinds expose the correct group, arg counts, and responses. */
    @Test
    fun testCommandKindModel() {
        assertEquals(DebugCommandGroup.Config, DebugCommandKind.SETB.group)
        assertEquals(2, DebugCommandKind.SETB.minArgs)
        assertEquals(2, DebugCommandKind.SETB.maxArgs)
        assertEquals(
            DebuggerResponseDataKind.Extended,
            DebugCommandKind.EXEC.responses[DebuggerStatus.OK],
        )
        assertEquals(DebugCommandGroup.Run, DebugCommandKind.STEP.group)
    }
}
