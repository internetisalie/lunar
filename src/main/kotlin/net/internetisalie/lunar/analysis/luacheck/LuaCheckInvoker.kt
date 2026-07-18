package net.internetisalie.lunar.analysis.luacheck

import com.intellij.openapi.diagnostic.logger
import net.internetisalie.lunar.toolchain.exec.LuaExecOutcome
import net.internetisalie.lunar.toolchain.exec.LuaExecResult
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService

object LuaCheckInvoker {
    private val LOG = logger<LuaCheckInvoker>()

    private val LINE_PATTERN = "(.+?):(\\d+):(\\d+)-(\\d+):(.+)".toRegex()
    private val ANSI_PATTERN = Regex("\\[[;\\d]*m")

    fun invoke(info: LuaCheckAnnotator.Info): LuaCheckOutcome {
        val cmd = newLuaCheckCommandLine(info.project, info.fileName, info.workDir, useStdin = true)
            ?: return LuaCheckOutcome.NotApplicable

        val result = LuaToolExecutionService.getInstance()
            .capture(cmd, LuaExecTimeout.FORMAT, stdin = info.documentText)
        return classify(result, info.fileName)
    }

    internal fun classify(result: LuaExecResult, fileName: String): LuaCheckOutcome = when (result.outcome) {
        LuaExecOutcome.START_FAILED -> LuaCheckOutcome.Failure(FailureKind.LAUNCH_FAILED, "Could not execute luacheck")
        LuaExecOutcome.TIMED_OUT ->
            LuaCheckOutcome.Failure(FailureKind.TIMED_OUT, "luacheck did not respond within ${timeoutSeconds()}s")
        LuaExecOutcome.CANCELLED -> LuaCheckOutcome.NotApplicable
        LuaExecOutcome.COMPLETED -> completedOutcome(result, fileName)
    }

    private fun completedOutcome(result: LuaExecResult, fileName: String): LuaCheckOutcome {
        if (result.exitCode >= FATAL_EXIT_CODE) {
            val detail = result.stderr.lineSequence().firstOrNull { it.isNotBlank() }
                ?: "luacheck exited with code ${result.exitCode}"
            return LuaCheckOutcome.Failure(FailureKind.CRASHED, detail)
        }
        return LuaCheckOutcome.Problems(parseProblems(result.stdout, fileName))
    }

    private fun parseProblems(stdout: String, fileName: String): List<Problem> =
        stdout.lineSequence().mapNotNull { line -> problemFrom(line, fileName) }.toList()

    private fun problemFrom(line: String, fileName: String): Problem? {
        val match = LINE_PATTERN.find(line) ?: return null
        val lineGroup = match.groups[2] ?: return null
        val colStartGroup = match.groups[3] ?: return null
        val colEndGroup = match.groups[4] ?: return null
        val descGroup = match.groups[5] ?: return null
        val message = descGroup.value.replace(ANSI_PATTERN, "")

        LOG.debug("line=${lineGroup.value} col=${colStartGroup.value}:${colEndGroup.value} msg=$message")
        return Problem(
            lineStart = lineGroup.value.toInt() - 1,
            lineEnd = lineGroup.value.toInt() - 1,
            columnStart = colStartGroup.value.toInt() - 1,
            columnEnd = colEndGroup.value.toInt() - 1,
            message = message,
            file = fileName,
        )
    }

    private fun timeoutSeconds(): Int = LuaExecTimeout.FORMAT.millis / 1000

    private const val FATAL_EXIT_CODE = 2
}
