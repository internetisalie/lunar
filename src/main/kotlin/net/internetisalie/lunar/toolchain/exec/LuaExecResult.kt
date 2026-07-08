package net.internetisalie.lunar.toolchain.exec

import com.intellij.execution.process.ProcessOutput

enum class LuaExecOutcome { COMPLETED, TIMED_OUT, START_FAILED, CANCELLED }

data class LuaExecResult(val output: ProcessOutput, val outcome: LuaExecOutcome) {
    val exitCode: Int get() = output.exitCode
    val stdout: String get() = output.stdout
    val stderr: String get() = output.stderr
    val isSuccess: Boolean get() = outcome == LuaExecOutcome.COMPLETED && output.exitCode == 0
}
