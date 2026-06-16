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
import net.internetisalie.lunar.rocks.run.LuaRocksSettings
import net.internetisalie.lunar.util.LuaProcessUtil

/**
 * Publishes the selected `.rockspec` to luarocks.org via `luarocks upload` (ROCKS-08).
 *
 * Gated to `.rockspec` files; resolves the API key from [LuaRocksApiKeyStore] (prompting on first
 * use), then runs the upload on a [Task.Backgroundable] off the EDT, reusing the ROCKS-04 binary
 * path and the shared `notification.group.lunar.luarocks` group for the result.
 */
class PublishRockAction : DumbAwareAction(
    "Publish Rock to LuaRocks…",
    "Upload this rockspec to luarocks.org",
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

        val apiKey = ensureApiKey(project) ?: return
        upload(project, rockspec.path, apiKey)
    }

    private fun ensureApiKey(project: Project): String? {
        LuaRocksApiKeyStore.getApiKey()?.let { return it }
        val entered = Messages.showPasswordDialog(
            project,
            "Enter your luarocks.org upload API key:",
            "Publish Rock to LuaRocks",
            LuaIcons.ROCKET,
        )?.takeIf { it.isNotBlank() } ?: return null
        LuaRocksApiKeyStore.setApiKey(entered)
        return entered
    }

    private fun upload(project: Project, rockspecPath: String, apiKey: String) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Publishing rock to LuaRocks", true) {
                override fun run(indicator: ProgressIndicator) {
                    val exe = LuaRocksSettings.getInstance().executablePath
                    val command = RockUploadCommand.build(exe, rockspecPath, apiKey)
                    val output = LuaProcessUtil.capture(command, UPLOAD_TIMEOUT_MS)
                    if (output.exitCode == 0) {
                        notify(project, "LuaRocks: published $rockspecPath", NotificationType.INFORMATION)
                    } else {
                        val stderr = output.stderr.trim().ifEmpty { "(no output)" }
                        notify(project, "LuaRocks publish failed: $stderr", NotificationType.ERROR)
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
        private const val UPLOAD_TIMEOUT_MS = 120_000

        /** True when [file] is a `.rockspec` (the upload target). */
        fun isRockspec(file: VirtualFile): Boolean =
            !file.isDirectory && file.extension == "rockspec"
    }
}
