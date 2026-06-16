package net.internetisalie.lunar.tool.health

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import net.internetisalie.lunar.tool.LuaTool
import net.internetisalie.lunar.tool.LuaToolManager
import net.internetisalie.lunar.util.newProjectBackgroundTask
import java.io.File

/**
 * Project-scoped service that drives background health monitoring for registered [LuaTool]s
 * (TOOL-03, design §2.5 and §3.2/§3.3).
 *
 * Responsibilities:
 * - On [start]: register an [AsyncFileListener] that watches tool binary paths for VFS events
 *   (delete / move / rename / content-change) and triggers [revalidateAll] reactively.
 * - [revalidateAll]: runs [LuaToolHealthChecker.check] on every registered tool in the background,
 *   writes results back, refreshes editor notifications, and emits a balloon if any tool became invalid.
 *
 * **Threading:** the listener's `prepareChange` is called on a background VFS thread; UI refresh
 * calls are posted to the EDT inside helper utilities.
 */
@Service(Service.Level.PROJECT)
class LuaToolHealthMonitor(private val project: Project) {

    private val LOG = logger<LuaToolHealthMonitor>()

    /**
     * Registers the reactive VFS listener and runs an initial [revalidateAll] pass.
     * Called from [LuaToolHealthStartup] on project open.
     */
    fun start() {
        VirtualFileManager.getInstance().addAsyncFileListener(buildFileListener(), project)
        LOG.debug("LuaToolHealthMonitor started for project '${project.name}'")
    }

    /**
     * Runs a health check on all registered tools in the background (TOOL-03-03/05).
     *
     * After all checks complete:
     * 1. Refreshes editor notification banners.
     * 2. If any tool became invalid, shows a single balloon notification.
     * 3. Logs a diagnostics snapshot via [LuaToolDiagnostics].
     */
    fun revalidateAll() {
        newProjectBackgroundTask("Validating Lua tools", project) { indicator ->
            indicator.isIndeterminate = true
            val tools = LuaToolManager.getInstance().getTools()
            val nowInvalid = mutableListOf<LuaTool>()

            for (tool in tools) {
                indicator.text2 = tool.path
                val wasValid = tool.isValid
                val result = LuaToolHealthChecker.check(tool)
                LuaToolHealthChecker.applyResult(tool, result)
                if (wasValid && !result.isValid) {
                    nowInvalid += tool
                }
            }

            // Post UI work to the EDT: refresh editor banners and (if anything regressed) warn once.
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                refreshEditorNotifications()
                if (nowInvalid.isNotEmpty()) {
                    showInvalidToolBalloon(nowInvalid)
                }
            }

            LuaToolDiagnostics.logSnapshot(project)
        }.queue()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun refreshEditorNotifications() {
        try {
            com.intellij.ui.EditorNotifications.getInstance(project).updateAllNotifications()
        } catch (e: Exception) {
            LOG.debug("Could not refresh editor notifications: ${e.message}")
        }
    }

    private fun showInvalidToolBalloon(invalidTools: List<LuaTool>) {
        val names = invalidTools.joinToString(", ") { it.type.name }
        val message = "Lua tool(s) became unavailable: $names. Check Settings > Lua > Tools."
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("notification.group.lunar.tools")
                ?.createNotification(message, NotificationType.WARNING)
                ?.notify(project)
        } catch (e: Exception) {
            LOG.warn("Could not show tool-invalid balloon: ${e.message}")
        }
    }

    private fun buildFileListener(): AsyncFileListener {
        return AsyncFileListener { events ->
            val watchedPaths = LuaToolManager.getInstance().getTools()
                .mapTo(HashSet()) { File(it.path).canonicalPath }

            val affectsTools = events.any { event ->
                isWatchedEvent(event) && isWatchedPath(event, watchedPaths)
            }

            if (!affectsTools) return@AsyncFileListener null

            object : AsyncFileListener.ChangeApplier {
                override fun afterVfsChange() {
                    LOG.debug("VFS change detected on a watched tool path; scheduling revalidation")
                    revalidateAll()
                }
            }
        }
    }

    private fun isWatchedEvent(event: VFileEvent): Boolean = when (event) {
        is VFileDeleteEvent,
        is VFileMoveEvent,
        is VFilePropertyChangeEvent,
        is VFileContentChangeEvent -> true
        else -> false
    }

    private fun isWatchedPath(event: VFileEvent, watchedPaths: Set<String>): Boolean {
        val path = try {
            event.file?.canonicalPath ?: event.path
        } catch (_: Exception) {
            event.path
        }
        return path in watchedPaths
    }

    companion object {
        fun getInstance(project: Project): LuaToolHealthMonitor = project.service()
    }
}
