package net.internetisalie.lunar.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestLuaDebugConnection {

    @Test
    fun testDebugCommandToString() {
        val cmd1 = DebugCommand(DebugCommandKind.STEP)
        assertEquals("STEP", cmd1.toString())

        val cmd2 = DebugCommand(DebugCommandKind.BASEDIR, listOf("/home/user"))
        assertEquals("BASEDIR /home/user", cmd2.toString())

        val cmd3 = DebugCommand(DebugCommandKind.SETB, listOf("main.lua", "10"))
        assertEquals("SETB main.lua 10", cmd3.toString())
    }

    @Test
    fun testDebuggerStatus() {
        assertTrue(DebuggerStatus.OK.code < 400)
        assertFalse(DebuggerStatus.OK.isError)

        assertTrue(DebuggerStatus.BadRequest.code >= 400)
        assertTrue(DebuggerStatus.BadRequest.isError)

        assertTrue(DebuggerStatus.ErrorInExecution.isError)
        assertTrue(DebuggerStatus.ErrorInExpression.isError)
        
        assertEquals(DebuggerStatus.OK, DebuggerStatus.entries.first { "200 OK".startsWith(it.message) })
    }
}
