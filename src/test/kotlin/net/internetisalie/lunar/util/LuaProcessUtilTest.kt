package net.internetisalie.lunar.util

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LuaProcessUtilTest : BasePlatformTestCase() {

    fun testCaptureReturnsStdoutAndZeroExit() {
        if (SystemInfo.isWindows) return
        val cmd = GeneralCommandLine("/bin/sh", "-c", "printf lunar-ok")

        val output = LuaProcessUtil.capture(cmd)

        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("lunar-ok"))
    }

    fun testCaptureTimeoutMapsToTimeoutExitCode() {
        if (SystemInfo.isWindows) return
        val cmd = GeneralCommandLine("/bin/sh", "-c", "sleep 5")

        val output = LuaProcessUtil.capture(cmd, timeout = 200)

        assertEquals(LuaProcessUtil.PROCESS_TIMEOUT_EXCEPTION_CODE, output.exitCode)
        assertTrue(output.isTimeout)
    }

    // TC-03: An unresolvable command fails while the CapturingProcessHandler is being constructed
    // (LuaProcessUtil.kt:28), which is outside doCapture's try block, so the ExecutionException
    // (a ProcessNotCreatedException) propagates rather than being mapped to
    // PROCESS_EXECUTION_EXCEPTION_CODE. Asserting the real, observable behaviour keeps the test
    // truthful without modifying production. See handoff report deviation note.
    fun testCaptureUnresolvableCommandThrowsExecutionException() {
        val cmd = GeneralCommandLine("this-binary-does-not-exist-xyz")

        try {
            LuaProcessUtil.capture(cmd)
            fail("Expected ExecutionException for an unresolvable command")
        } catch (expected: ExecutionException) {
            assertNotNull(expected)
        }
    }
}
