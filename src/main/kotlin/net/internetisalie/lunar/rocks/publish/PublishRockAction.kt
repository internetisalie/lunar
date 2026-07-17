package net.internetisalie.lunar.rocks.publish

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.rocks.LuaRocksEnvironment
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService

/**
 * Publishes the selected `.rockspec` to a LuaRocks registry via `luarocks upload` (ROCKS-08,
 * ROCKS-06).
 *
 * Gated to `.rockspec` files; resolves the effective registry server and executable via
 * [LuaRocksEnvironment], retrieves (or prompts for) the per-server API key from
 * [LuaRocksApiKeyStore], then runs the upload on a [Task.Backgroundable] off the EDT.
 */
class PublishRockAction : DumbAwareAction(
    "Publish Rock to LuaRocks…",
    "Upload this rockspec to LuaRocks",
    LuaIcons.ROCKET,
) {
    override fun update(event: AnActionEvent) {
        val rockspec = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val enabled = event.project != null && rockspec != null && isRockspec(rockspec)
        event.presentation.isEnabledAndVisible = enabled
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val rockspec = event.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { isRockspec(it) } ?: return

        val server = LuaRocksEnvironment.resolveServer(project)
        val apiKey = ensureApiKey(project, server) ?: return
        upload(project, rockspec.path, apiKey, server)
    }

    private fun ensureApiKey(project: Project, server: String?): String? {
        LuaRocksApiKeyStore.getApiKey(server)?.let { return it }
        val prompt = if (server != null) "Enter your API key for $server:" else "Enter your luarocks.org upload API key:"
        val entered = Messages.showPasswordDialog(
            project,
            prompt,
            "Publish Rock to LuaRocks",
            LuaIcons.ROCKET,
        )?.takeIf { it.isNotBlank() } ?: return null
        LuaRocksApiKeyStore.setApiKey(server, entered)
        return entered
    }

    private fun upload(project: Project, rockspecPath: String, apiKey: String, server: String?) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Publishing rock to LuaRocks", true) {
                override fun run(indicator: ProgressIndicator) {
                    val exe = LuaRocksEnvironment.resolveExecutable(project)
                    if (exe == null) {
                        notify(project, LUAROCKS_NOT_CONFIGURED, NotificationType.ERROR)
                        return
                    }
                    val command = RockUploadCommand.build(exe, rockspecPath, apiKey, server = server)
                    val output = LuaToolExecutionService.getInstance()
                        .capture(command, LuaExecTimeout.NETWORK, indicator = indicator)
                    if (output.exitCode == 0) {
                        notify(project, "LuaRocks: published $rockspecPath", NotificationType.INFORMATION)
                    } else {
                        val stderr = output.stderr.trim().ifEmpty { "(no output)" }
                        if (isAuthFailure(output.exitCode, output.stderr)) {
                            LuaRocksApiKeyStore.setApiKey(server, null)
                            notify(
                                project,
                                "LuaRocks publish failed (auth error — stored API key cleared): $stderr. " +
                                    "Run Publish again to re-enter the key.",
                                NotificationType.ERROR,
                            )
                        } else {
                            notify(project, "LuaRocks publish failed: $stderr", NotificationType.ERROR)
                        }
                    }
                }
            },
        )
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, type)
            .notify(project)
    }

    companion object {
        private const val NOTIFICATION_GROUP = "notification.group.lunar.luarocks"
        private const val LUAROCKS_NOT_CONFIGURED =
            "LuaRocks is not configured. Register or bind it under " +
                "Settings | Languages & Frameworks | Lua | Toolchain."

        /** True when [file] is a `.rockspec` (the upload target). */
        fun isRockspec(file: VirtualFile): Boolean =
            !file.isDirectory && file.extension == "rockspec"

        /**
         * Returns true when a non-zero [exitCode] with [stderr] output looks like an authentication
         * failure (BUG-376). Used to clear the stored API key and prompt the user to re-enter it.
         *
         * Matches patterns emitted by `luarocks upload` on a bad or revoked key:
         * - "Invalid API key"
         * - "Authentication failed" / "auth"
         * - "Forbidden" (HTTP 403)
         * - "Unauthorized" (HTTP 401)
         */
        internal fun isAuthFailure(exitCode: Int, stderr: String): Boolean {
            if (exitCode == 0) return false
            val lower = stderr.lowercase()
            return lower.contains("invalid api key") ||
                lower.contains("authentication failed") ||
                lower.contains("unauthorized") ||
                lower.contains("forbidden") ||
                (lower.contains("auth") && lower.contains("key"))
        }
    }
}
