package net.internetisalie.lunar.rocks.browser

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.run.LuaRocksSettings
import net.internetisalie.lunar.util.LuaProcessUtil

/**
 * Runs `luarocks install` / `luarocks remove` on a background progress task (ROCKS-02-04).
 *
 * After success: invalidates [LuaRocksSearchCache] and fires [onDone](true).
 * After failure: fires [onDone](false) and shows an error notification with stderr.
 *
 * Install uses a 2-minute timeout (packages can be large); the ROCKS-04 run config is the
 * right venue for long/interactive installs that need a live console.
 */
object LuaRocksActionHandler {
    private const val INSTALL_TIMEOUT_MS = 120_000
    private const val NOTIFICATION_GROUP = "notification.group.lunar.luarocks"

    /**
     * Installs [name] (at [version] if specified) in the background.
     * [onDone] is called on the EDT with `true` on success, `false` on failure.
     */
    fun install(project: Project, name: String, version: String?, onDone: (Boolean) -> Unit) {
        val title = if (version != null) "Installing $name $version" else "Installing $name"
        runInBackground(project, title) {
            val exe = LuaRocksSettings.getInstance().executablePath
            val args = buildList {
                add(exe); add("install"); add(name)
                if (version != null) add(version)
            }
            val output = LuaProcessUtil.capture(GeneralCommandLine(args), INSTALL_TIMEOUT_MS)
            if (output.exitCode == 0) {
                LuaRocksSearchCache.invalidateAll()
                notify(project, "LuaRocks: installed $name${version?.let { " $it" } ?: ""}", NotificationType.INFORMATION)
                onDone(true)
            } else {
                val stderr = output.stderr.trim().ifEmpty { "(no output)" }
                notify(project, "LuaRocks install failed: $stderr", NotificationType.ERROR)
                onDone(false)
            }
        }
    }

    /**
     * Removes [name] in the background.
     * [onDone] is called on the EDT with `true` on success, `false` on failure.
     */
    fun uninstall(project: Project, name: String, onDone: (Boolean) -> Unit) {
        runInBackground(project, "Removing $name") {
            val exe = LuaRocksSettings.getInstance().executablePath
            val output = LuaProcessUtil.capture(
                GeneralCommandLine(exe, "remove", name),
                INSTALL_TIMEOUT_MS,
            )
            if (output.exitCode == 0) {
                LuaRocksSearchCache.invalidateAll()
                notify(project, "LuaRocks: removed $name", NotificationType.INFORMATION)
                onDone(true)
            } else {
                val stderr = output.stderr.trim().ifEmpty { "(no output)" }
                notify(project, "LuaRocks remove failed: $stderr", NotificationType.ERROR)
                onDone(false)
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun runInBackground(project: Project, title: String, block: () -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, false) {
            override fun run(indicator: ProgressIndicator) = block()
        })
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, type)
            .notify(project)
    }
}
