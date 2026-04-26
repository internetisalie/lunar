package net.internetisalie.lunar.run

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
        val execLatch = CountDownLatch(1)
        val resumedLatch = CountDownLatch(1)

        var pausedPos: LuaPosition? = null
        var execResult: String? = null

        val observer = object : LuaDebugObserver {
            override fun onCommandComplete(command: DebugCommand, status: DebuggerStatus, data: String) {
                when (command.kind) {
                    DebugCommandKind.EXEC -> {
                        execResult = data
                        execLatch.countDown()
                    }
                    DebugCommandKind.RUN -> resumedLatch.countDown()
                    else -> {}
                }
            }
            override fun onCommandCancelled(command: DebugCommand) {}
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
            harness.connection.queue(DebugCommand(DebugCommandKind.BASEDIR, listOf(baseDir)))
            harness.connection.queue(DebugCommand(DebugCommandKind.SETB, listOf(script.name, "2")))
            harness.connection.queue(DebugCommand(DebugCommandKind.RUN))

            assertTrue(pausedLatch.await(4, TimeUnit.SECONDS), "Expected pause at breakpoint")
            assertNotNull(pausedPos)
            assertEquals(2, pausedPos.line)

            harness.connection.queue(DebugCommand(DebugCommandKind.EXEC, listOf("return x")))
            assertTrue(execLatch.await(4, TimeUnit.SECONDS), "Expected EXEC result")
            assertNotNull(execResult)

            // Resume execution after the inspect; RUN continues from the breakpoint
            harness.connection.queue(DebugCommand(DebugCommandKind.RUN))
            assertTrue(resumedLatch.await(4, TimeUnit.SECONDS), "Expected RUN acknowledgement after EXEC")
        }
    }
}
