package net.internetisalie.lunar.run

import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestLuaDebugHarness {

    @Test
    fun testBreakpointAndExec() {
        val script = File.createTempFile("lunar_test_", ".lua").also {
            it.deleteOnExit()
            it.writeText(
                """
                local x = 1
                x = x + 1
                x = x + 1
                """.trimIndent()
            )
        }

        val pausedLatch = CountDownLatch(1)
        var pausedPos: LuaPosition? = null

        // Command results now come back directly from suspend send(); the observer carries only
        // the out-of-band pause/error/disconnect events (MAINT-22).
        val observer = object : LuaDebugObserver {
            override fun onPauseBreakpoint(pos: LuaPosition) {
                pausedPos = pos
                pausedLatch.countDown()
            }
            override fun onPauseWatchpoint(pos: LuaPosition, watchIndex: Int) {}
            override fun onRunExecutionError(file: String) {}
            override fun onDisconnected() {}
        }

        // BASEDIR must have a trailing slash so mobdebug strips the directory prefix
        // correctly, leaving just the filename to match the SETB argument.
        val baseDir = "${script.parent}/"

        startLuaDebugHarness(script, observer).use { harness ->
            val connection = harness.connection

            // Configure breakpoints and start running; each send() returns when the debuggee acks.
            runBlocking {
                connection.send(DebugCommand(DebugCommandKind.BASEDIR, listOf(baseDir)))
                connection.send(DebugCommand(DebugCommandKind.SETB, listOf(script.name, "2")))
                connection.send(DebugCommand(DebugCommandKind.RUN))
            }

            // RUN is acked immediately; the pause arrives out-of-band via the observer.
            assertTrue(pausedLatch.await(4, TimeUnit.SECONDS), "Expected pause at breakpoint")
            val pos = assertNotNull(pausedPos)
            assertEquals(2, pos.line)

            // EXEC returns its result directly.
            val execResult = runBlocking {
                connection.send(DebugCommand(DebugCommandKind.EXEC, listOf("return x")))
            }
            assertNotNull(execResult)

            // Resume execution after the inspect; RUN returning without error is the acknowledgement.
            runBlocking { connection.send(DebugCommand(DebugCommandKind.RUN)) }
        }
    }
}
