package net.internetisalie.lunar.util

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

    // TC-03: An unresolvable command's launch failure (ExecutionException, thrown by the
    // CapturingProcessHandler construction inside doCapture's try block) maps to
    // PROCESS_EXECUTION_EXCEPTION_CODE with isTimeout == false.
    fun testCaptureUnresolvableCommandMapsToExecutionExitCode() {
        val cmd = GeneralCommandLine("this-binary-does-not-exist-xyz")

        val output = LuaProcessUtil.capture(cmd)

        assertEquals(LuaProcessUtil.PROCESS_EXECUTION_EXCEPTION_CODE, output.exitCode)
        assertFalse(output.isTimeout)
    }
}
