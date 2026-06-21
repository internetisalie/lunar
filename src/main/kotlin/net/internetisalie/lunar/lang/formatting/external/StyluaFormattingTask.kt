package net.internetisalie.lunar.lang.formatting.external

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.AsyncDocumentFormattingService.FormattingTask
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import net.internetisalie.lunar.util.LuaProcessUtil
import java.io.IOException
import java.nio.charset.StandardCharsets

data class StyluaExecutionConfig(
    val styluaPath: String,
    val fileName: String,
    val workingDirectory: String,
)

class StyluaFormattingTask(
    private val request: AsyncFormattingRequest,
    private val config: StyluaExecutionConfig,
) : FormattingTask {

    private val LOG = logger<StyluaFormattingTask>()

    @Volatile
    private var processHandler: CapturingProcessHandler? = null

    override fun run() {
        val cmd = buildCommandLine()
        val stdin = request.documentText
        val handler = try {
            CapturingProcessHandler(cmd)
        } catch (e: ExecutionException) {
            LOG.warn("Failed to create process for Stylua: ${e.message}", e)
            request.onError("Stylua", "Could not execute stylua at ${config.styluaPath}")
            return
        }

        this.processHandler = handler
        setupStdinWriter(handler, stdin)

        val output = try {
            handler.runProcess(timeoutMs, true)
        } catch (e: Exception) {
            LOG.warn("Error running Stylua process: ${e.message}", e)
            ProcessOutput("", "", LuaProcessUtil.PROCESS_EXECUTION_EXCEPTION_CODE, false, false)
        }

        handleProcessOutput(output)
    }

    private fun buildCommandLine(): GeneralCommandLine {
        return GeneralCommandLine(config.styluaPath)
            .withWorkDirectory(config.workingDirectory)
            .withParameters("--stdin-filepath", config.fileName)
            .withCharset(StandardCharsets.UTF_8)
    }

    private fun setupStdinWriter(handler: CapturingProcessHandler, stdin: String) {
        handler.addProcessListener(object : ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                try {
                    val processInput = event.processHandler.processInput ?: return
                    processInput.writer(StandardCharsets.UTF_8).use {
                        it.write(stdin)
                    }
                } catch (e: IOException) {
                    LOG.warn("Failed to write stdin to Stylua process: ${e.message}", e)
                }
            }
        })
    }

    private fun handleProcessOutput(output: ProcessOutput) {
        val exitCode = output.exitCode
        if (exitCode == 0) {
            request.onTextReady(output.stdout)
            showFirstUseNotificationIfNeeded()
        } else if (output.isTimeout) {
            request.onError("Stylua Timeout", "Stylua did not respond within 30 seconds")
        } else if (exitCode == LuaProcessUtil.PROCESS_EXECUTION_EXCEPTION_CODE) {
            request.onError("Stylua", "Could not execute stylua at ${config.styluaPath}")
        } else {
            val stderr = output.stderr
            val firstLine = stderr.lineSequence().firstOrNull { it.isNotBlank() } ?: "Stylua exited with code $exitCode"
            request.onError("Stylua", firstLine)
        }
    }

    private fun showFirstUseNotificationIfNeeded() {
        val properties = PropertiesComponent.getInstance()
        val key = "lunar.stylua.firstUse.notified"
        if (!properties.getBoolean(key)) {
            properties.setValue(key, true)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("notification.group.lunar.stylua")
                ?.createNotification("Formatted with Stylua", NotificationType.INFORMATION)
                ?.notify(null) // Project-less notification since FormattingTask lacks Project in some contexts
        }
    }

    override fun cancel(): Boolean {
        val handler = processHandler
        if (handler != null) {
            handler.destroyProcess()
            return true
        }
        return false
    }

    override fun isRunUnderProgress(): Boolean = false

    companion object {
        var timeoutMs: Int = 30_000
    }
}
