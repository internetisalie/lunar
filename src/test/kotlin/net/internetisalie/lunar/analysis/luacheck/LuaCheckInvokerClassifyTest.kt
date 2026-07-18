package net.internetisalie.lunar.analysis.luacheck

import com.intellij.execution.process.ProcessOutput
import net.internetisalie.lunar.toolchain.exec.LuaExecOutcome
import net.internetisalie.lunar.toolchain.exec.LuaExecResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MAINT-26-06 (§3.5) — luacheck exec-result classification. Launch failure and a fatal exit
 * (≥ 2) must map to a surfaced [LuaCheckOutcome.Failure]; exit 0/1 to [LuaCheckOutcome.Problems]
 * with the summary line ignored. TC10–TC12.
 */
class LuaCheckInvokerClassifyTest {

    private fun result(outcome: LuaExecOutcome, exitCode: Int, stdout: String = "", stderr: String = ""): LuaExecResult {
        val output = ProcessOutput(stdout, stderr, exitCode, false, false)
        return LuaExecResult(output, outcome)
    }

    @Test
    fun `test TC10 START_FAILED maps to LAUNCH_FAILED`() {
        val outcome = LuaCheckInvoker.classify(result(LuaExecOutcome.START_FAILED, -1), "f.lua")
        val failure = outcome as LuaCheckOutcome.Failure
        assertEquals(FailureKind.LAUNCH_FAILED, failure.kind)
    }

    @Test
    fun `test TC11 completed exit 2 maps to CRASHED with stderr detail`() {
        val outcome = LuaCheckInvoker.classify(result(LuaExecOutcome.COMPLETED, 2, stderr = "bad std"), "f.lua")
        val failure = outcome as LuaCheckOutcome.Failure
        assertEquals(FailureKind.CRASHED, failure.kind)
        assertEquals("bad std", failure.detail)
    }

    @Test
    fun `test TC12 completed exit 1 parses problems and ignores the summary line`() {
        val stdout = buildString {
            appendLine("f.lua:1:7-7: (W211) unused variable 'x'")
            appendLine("f.lua:2:1-3: (W113) accessing undefined variable 'foo'")
            appendLine("Total: 2 warnings / 0 errors in 1 file")
        }
        val outcome = LuaCheckInvoker.classify(result(LuaExecOutcome.COMPLETED, 1, stdout = stdout), "f.lua")
        val problems = outcome as LuaCheckOutcome.Problems
        assertEquals(2, problems.problems.size)
        assertTrue(problems.problems.all { it.file == "f.lua" })
    }
}
