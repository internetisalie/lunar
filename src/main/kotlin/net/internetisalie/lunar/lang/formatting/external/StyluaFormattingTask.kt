package net.internetisalie.lunar.lang.formatting.external

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.AsyncDocumentFormattingService.FormattingTask
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import net.internetisalie.lunar.toolchain.exec.LuaExecOutcome
import net.internetisalie.lunar.toolchain.exec.LuaExecResult
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable

data class StyluaExecutionConfig(
    val styluaPath: String,
    val fileName: String,
    val workingDirectory: String,
)

class StyluaFormattingTask(
    private val request: AsyncFormattingRequest,
    private val config: StyluaExecutionConfig,
) : FormattingTask {

    override fun run() {
        handleResult(captureOffEdt())
    }

    private fun captureOffEdt(): LuaExecResult {
        val cmd = buildCommandLine()
        val text = request.documentText
        val service = LuaToolExecutionService.getInstance()
        val application = ApplicationManager.getApplication()
        // AsyncDocumentFormattingService normally runs this off the EDT; the light-fixture
        // formatter path drives it on the EDT, where the exec service (rightly) refuses to
        // run — offload so process I/O never touches the UI thread (contract §1/§10).
        return if (application != null && application.isDispatchThread) {
            application.executeOnPooledThread(Callable { service.captureWithMillis(cmd, timeoutMs, text) }).get()
        } else {
            service.captureWithMillis(cmd, timeoutMs, text)
        }
    }

    private fun buildCommandLine(): GeneralCommandLine {
        return GeneralCommandLine(config.styluaPath)
            .withWorkDirectory(config.workingDirectory)
            .withParameters("--stdin-filepath", config.fileName)
            .withCharset(StandardCharsets.UTF_8)
    }

    private fun handleResult(result: LuaExecResult) {
        when {
            result.outcome == LuaExecOutcome.START_FAILED ->
                request.onError("Stylua", "Could not execute stylua at ${config.styluaPath}")
            result.outcome == LuaExecOutcome.TIMED_OUT ->
                request.onError("Stylua Timeout", "Stylua did not respond within 30 seconds")
            result.exitCode == 0 -> {
                request.onTextReady(result.stdout)
                showFirstUseNotificationIfNeeded()
            }
            else -> {
                val firstLine = result.stderr.lineSequence().firstOrNull { it.isNotBlank() }
                    ?: "Stylua exited with code ${result.exitCode}"
                request.onError("Stylua", firstLine)
            }
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

    override fun cancel(): Boolean = false

    override fun isRunUnderProgress(): Boolean = false

    companion object {
        var timeoutMs: Int = LuaExecTimeout.FORMAT.millis
    }
}
