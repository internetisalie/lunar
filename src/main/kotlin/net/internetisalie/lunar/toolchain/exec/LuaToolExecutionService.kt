package net.internetisalie.lunar.toolchain.exec

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable
import java.util.concurrent.TimeoutException

@Service(Service.Level.APP)
class LuaToolExecutionService {

    fun capture(
        cmd: GeneralCommandLine,
        timeout: LuaExecTimeout = LuaExecTimeout.COMMAND,
        stdin: String? = null,
        indicator: ProgressIndicator? = null,
    ): LuaExecResult = captureWithMillis(cmd, timeout.millis, stdin, indicator)

    fun stream(
        cmd: GeneralCommandLine,
        listener: ProcessListener,
        timeout: LuaExecTimeout = LuaExecTimeout.COMMAND,
        colored: Boolean = false,
        indicator: ProgressIndicator? = null,
    ): LuaExecResult = streamWithMillis(cmd, listener, timeout.millis, colored, indicator)

    @TestOnly
    internal fun captureWithMillis(
        cmd: GeneralCommandLine,
        millis: Int,
        stdin: String? = null,
        indicator: ProgressIndicator? = null,
    ): LuaExecResult {
        ThreadingAssertions.softAssertBackgroundThread()
        val application = ApplicationManager.getApplication()
        if (application != null && application.isReadAccessAllowed && !application.isDispatchThread) {
            return application.executeOnPooledThread(Callable { doCapture(cmd, millis, stdin, indicator) }).get()
        }
        return doCapture(cmd, millis, stdin, indicator)
    }

    private fun doCapture(
        cmd: GeneralCommandLine,
        millis: Int,
        stdin: String?,
        indicator: ProgressIndicator?,
    ): LuaExecResult {
        val handler = try {
            CapturingProcessHandler(cmd)
        } catch (failure: ExecutionException) {
            return LuaExecResult(ProcessOutput("", failure.message ?: "", -1, false, false), LuaExecOutcome.START_FAILED)
        }
        writeStdin(handler, stdin)
        val effectiveIndicator = indicator ?: ProgressManager.getInstance().progressIndicator
        val output = try {
            if (effectiveIndicator != null) {
                handler.runProcessWithProgressIndicator(effectiveIndicator, millis, true)
            } else {
                handler.runProcess(millis, true)
            }
        } catch (_: TimeoutException) {
            return LuaExecResult(ProcessOutput("", "", -1, true, false), LuaExecOutcome.TIMED_OUT)
        }
        return LuaExecResult(output, outcomeOf(output))
    }

    private fun writeStdin(handler: CapturingProcessHandler, stdin: String?) {
        if (stdin == null) return
        handler.processInput.use { it.write(stdin.toByteArray(Charsets.UTF_8)) }
    }

    private fun outcomeOf(output: ProcessOutput): LuaExecOutcome = when {
        output.isCancelled -> LuaExecOutcome.CANCELLED
        output.isTimeout -> LuaExecOutcome.TIMED_OUT
        else -> LuaExecOutcome.COMPLETED
    }

    @TestOnly
    internal fun streamWithMillis(
        cmd: GeneralCommandLine,
        listener: ProcessListener,
        millis: Int,
        colored: Boolean = false,
        indicator: ProgressIndicator? = null,
    ): LuaExecResult {
        ThreadingAssertions.softAssertBackgroundThread()
        val handler = try {
            createStreamHandler(cmd, colored)
        } catch (failure: ExecutionException) {
            return LuaExecResult(ProcessOutput("", failure.message ?: "", -1, false, false), LuaExecOutcome.START_FAILED)
        }
        handler.addProcessListener(listener)
        handler.startNotify()
        return awaitStream(handler, millis, indicator)
    }

    private fun createStreamHandler(cmd: GeneralCommandLine, colored: Boolean): ProcessHandler =
        if (colored) ProcessHandlerFactory.getInstance().createColoredProcessHandler(cmd) else OSProcessHandler(cmd)

    private fun awaitStream(handler: ProcessHandler, millis: Int, indicator: ProgressIndicator?): LuaExecResult {
        var elapsed = 0
        while (!handler.waitFor(SLICE_MILLIS.toLong())) {
            if (indicator?.isCanceled == true) return destroyed(handler, LuaExecOutcome.CANCELLED)
            elapsed += SLICE_MILLIS
            if (elapsed >= millis) return destroyed(handler, LuaExecOutcome.TIMED_OUT)
        }
        return LuaExecResult(ProcessOutput("", "", handler.exitCode ?: -1, false, false), LuaExecOutcome.COMPLETED)
    }

    private fun destroyed(handler: ProcessHandler, outcome: LuaExecOutcome): LuaExecResult {
        handler.destroyProcess()
        handler.waitFor()
        val timeout = outcome == LuaExecOutcome.TIMED_OUT
        val cancelled = outcome == LuaExecOutcome.CANCELLED
        return LuaExecResult(ProcessOutput("", "", handler.exitCode ?: -1, timeout, cancelled), outcome)
    }

    companion object {
        private const val SLICE_MILLIS = 100

        fun getInstance(): LuaToolExecutionService =
            ApplicationManager.getApplication().getService(LuaToolExecutionService::class.java)
    }
}
