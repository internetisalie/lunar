package net.internetisalie.lunar.toolchain.exec

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.Key
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LuaToolExecutionServiceTest : BasePlatformTestCase() {

    private val service = LuaToolExecutionService()

    private fun sh(script: String): GeneralCommandLine = GeneralCommandLine("/bin/sh", "-c", script)

    private fun <T> onPooledThread(body: () -> T): T =
        ApplicationManager.getApplication().executeOnPooledThread<T>(body).get(30, TimeUnit.SECONDS)

    private class RecordingListener : ProcessListener {
        val text = StringBuilder()
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            synchronized(text) { text.append(event.text) }
        }
    }

    // TC 1
    fun testCaptureNonZeroExit() {
        val result = onPooledThread { service.capture(sh("echo out; echo err 1>&2; exit 3")) }
        assertEquals(LuaExecOutcome.COMPLETED, result.outcome)
        assertEquals(3, result.exitCode)
        assertEquals("out\n", result.stdout)
        assertEquals("err\n", result.stderr)
        assertFalse(result.isSuccess)
    }

    // TC 2
    fun testCaptureZeroExitIsSuccess() {
        val result = onPooledThread { service.capture(sh("echo out; echo err 1>&2; exit 0")) }
        assertEquals(LuaExecOutcome.COMPLETED, result.outcome)
        assertTrue(result.isSuccess)
    }

    // TC 3
    fun testCaptureUnresolvableCommandStartFailed() {
        val result = onPooledThread { service.capture(GeneralCommandLine("/nonexistent/binary-xyz")) }
        assertEquals(LuaExecOutcome.START_FAILED, result.outcome)
        assertFalse(result.isSuccess)
    }

    // TC 4
    fun testTimeoutClassMillis() {
        assertEquals(10_000, LuaExecTimeout.PROBE.millis)
        assertEquals(15_000, LuaExecTimeout.COMMAND.millis)
        assertEquals(30_000, LuaExecTimeout.FORMAT.millis)
        assertEquals(120_000, LuaExecTimeout.NETWORK.millis)
        assertEquals(600_000, LuaExecTimeout.INSTALL.millis)
    }

    // TC 5
    fun testCaptureTimesOutAndDestroys() {
        val started = System.currentTimeMillis()
        val result = onPooledThread { service.captureWithMillis(sh("sleep 5"), 200) }
        val elapsed = System.currentTimeMillis() - started
        assertEquals(LuaExecOutcome.TIMED_OUT, result.outcome)
        assertTrue("expected prompt return, took ${elapsed}ms", elapsed < 4_000)
    }

    // TC 6
    fun testCaptureCancelledViaIndicator() {
        val indicator = EmptyProgressIndicator()
        val canceller = Thread {
            Thread.sleep(200)
            indicator.cancel()
        }
        val started = System.currentTimeMillis()
        canceller.start()
        val result = onPooledThread { service.capture(sh("sleep 5"), LuaExecTimeout.COMMAND, indicator = indicator) }
        val elapsed = System.currentTimeMillis() - started
        assertEquals(LuaExecOutcome.CANCELLED, result.outcome)
        assertTrue(result.output.isCancelled)
        assertTrue("expected prompt cancellation, took ${elapsed}ms", elapsed < 4_000)
    }

    // TC 7
    fun testCaptureOnEdtLogsSoftAssert() {
        val logged = AtomicReference<String>()
        LoggedErrorProcessor.executeWith<RuntimeException>(
            object : LoggedErrorProcessor() {
                override fun processError(
                    category: String,
                    message: String,
                    details: Array<String>,
                    t: Throwable?,
                ): Set<Action> {
                    logged.set(message)
                    return Action.NONE
                }
            },
        ) {
            service.capture(sh("exit 0"))
        }
        assertNotNull("softAssertBackgroundThread error should have been logged on the EDT", logged.get())
    }

    // TC 8
    fun testStreamFeedsListenerAndReportsExit() {
        val listener = RecordingListener()
        val result = onPooledThread { service.stream(sh("printf a; printf b 1>&2; exit 2"), listener) }
        assertEquals(LuaExecOutcome.COMPLETED, result.outcome)
        assertEquals(2, result.exitCode)
        val captured = synchronized(listener.text) { listener.text.toString() }
        assertTrue("listener should receive stdout 'a', got '$captured'", captured.contains("a"))
        assertTrue("listener should receive stderr 'b', got '$captured'", captured.contains("b"))
    }

    // TC 9
    fun testStreamTimesOutAndDestroys() {
        val listener = RecordingListener()
        val started = System.currentTimeMillis()
        val result = onPooledThread { service.streamWithMillis(sh("sleep 5"), listener, 200) }
        val elapsed = System.currentTimeMillis() - started
        assertEquals(LuaExecOutcome.TIMED_OUT, result.outcome)
        assertTrue("expected prompt timeout, took ${elapsed}ms", elapsed < 4_000)
    }

    // TC 23
    fun testCaptureWritesStdin() {
        val result = onPooledThread {
            service.capture(GeneralCommandLine("cat"), LuaExecTimeout.COMMAND, stdin = "return 1\n")
        }
        assertEquals(LuaExecOutcome.COMPLETED, result.outcome)
        assertEquals("return 1\n", result.output.stdout)
    }
}
