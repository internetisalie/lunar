package net.internetisalie.lunar.util

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import java.util.concurrent.TimeoutException

object LuaProcessUtil {
    const val STANDARD_TIMEOUT: Int = 5 * 1000
    const val PROCESS_TIMEOUT_EXCEPTION_CODE: Int = -1
    const val PROCESS_EXECUTION_EXCEPTION_CODE: Int = -2

    // Execute and return the captured process output
    fun capture(cmd: GeneralCommandLine, timeout: Int = STANDARD_TIMEOUT): ProcessOutput {
        val app = com.intellij.openapi.application.ApplicationManager.getApplication()
        if (app != null && app.isReadAccessAllowed && !app.isDispatchThread) {
            return app.executeOnPooledThread(java.util.concurrent.Callable {
                doCapture(cmd, timeout)
            }).get()
        }
        return doCapture(cmd, timeout)
    }

    private fun doCapture(cmd: GeneralCommandLine, timeout: Int): ProcessOutput {
        val processHandler = CapturingProcessHandler(cmd)

        return try {
            if (timeout < 0) processHandler.runProcess() else processHandler.runProcess(timeout)
        } catch (_: TimeoutException) {
            ProcessOutput("", "", PROCESS_TIMEOUT_EXCEPTION_CODE, true, false)
        } catch (_: ExecutionException) {
            ProcessOutput("", "", PROCESS_EXECUTION_EXCEPTION_CODE, false, false)
        }
    }

    // Execute and provide a stream of process output to the listener
    // Returns true if the process terminated.
    fun listen(cmd: GeneralCommandLine, processListener: ProcessListener, timeout: Int = STANDARD_TIMEOUT) : Boolean {
        val handler = OSProcessHandler(cmd)
        handler.addProcessListener(processListener)
        handler.startNotify()
        return if (timeout > 0) {
            handler.waitFor(timeout.toLong())
        } else {
            handler.waitFor()
        }
    }
}
