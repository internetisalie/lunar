package net.internetisalie.lunar.toolchain.health

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.EditorNotifications
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.isUsable
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import net.internetisalie.lunar.toolchain.registry.LuaToolchainEvent
import net.internetisalie.lunar.toolchain.registry.LuaToolchainListener
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import net.internetisalie.lunar.util.newProjectBackgroundTask
import java.io.File

private val LOG = logger<LuaToolHealthMonitor>()

private const val NOTIFICATION_GROUP = "notification.group.lunar.tools"
private const val MERGE_WINDOW_MS = 500

/**
 * Project-scoped reactive health monitor (design §2.2): watches inventory binaries plus environment
 * root/bin dirs over an [AsyncFileListener], batches matching events through a
 * [MergingUpdateQueue], and revalidates each tool by writing results **only** through
 * [LuaToolchainRegistry.updateToolCheck]. Fires deduped balloons on usable→unusable transitions and
 * on environment-root deletion, and marshals banner refreshes to the EDT.
 */
@Service(Service.Level.PROJECT)
class LuaToolHealthMonitor(private val project: Project) : Disposable {

    @Volatile
    var runtimeBannerDismissed: Boolean = false
        private set

    private val notifiedDeletedEnvIds = mutableSetOf<String>()

    @Volatile
    private var watchSet: LuaHealthWatchSet = LuaHealthWatchSet.EMPTY

    private val revalidationQueue: MergingUpdateQueue = MergingUpdateQueue(
        "lunar.toolchain.health",
        MERGE_WINDOW_MS,
        true,
        null,
        this,
        null,
        Alarm.ThreadToUse.POOLED_THREAD
    )

    fun start() {
        rebuildWatchSet()
        VirtualFileManager.getInstance().addAsyncFileListener(HealthFileListener(), this)
        project.messageBus.connect(this).subscribe(
            LuaToolchainListener.TOPIC,
            object : LuaToolchainListener {
                override fun toolchainChanged(event: LuaToolchainEvent) {
                    rebuildWatchSet()
                    refreshBannersOnEdt()
                }
            }
        )
    }

    fun scheduleRevalidation() {
        revalidationQueue.queue(Update.create("revalidate") { revalidateAll() })
    }

    fun revalidateAll() {
        newProjectBackgroundTask("Validating Lua toolchain", project) { indicator ->
            runCatching { runRevalidationPass(indicator) }
                .onFailure { LOG.warn("Toolchain revalidation failed", it) }
        }.queue()
    }

    /**
     * Synchronous revalidation on the calling (background) thread. Test entry point that skips the
     * [newProjectBackgroundTask] scheduling; production callers use [revalidateAll].
     */
    @org.jetbrains.annotations.TestOnly
    fun revalidateNow(indicator: ProgressIndicator) {
        runRevalidationPass(indicator)
    }

    fun dismissRuntimeBanner() {
        runtimeBannerDismissed = true
    }

    private fun runRevalidationPass(indicator: ProgressIndicator) {
        val tools = LuaToolchainRegistry.getInstance().tools()
        val envs = LuaToolchainProjectSettings.getInstance(project).environments()
        val deadRoots = envs.filterNot { File(it.rootDir).isDirectory }
        val outcome = RevalidationOutcome()
        for (tool in tools) {
            indicator.checkCanceled()
            indicator.text2 = tool.path
            checkAndWriteTool(tool, deadRoots, outcome.newlyBroken)
        }
        val deadEnvsToNotify = collectDeadEnvNotifications(envs, deadRoots)
        marshalUiUpdates(outcome.newlyBroken, deadEnvsToNotify)
        LuaToolDiagnostics.logSnapshot(project)
    }

    private fun checkAndWriteTool(
        tool: LuaRegisteredTool,
        deadRoots: List<LuaEnvironmentState>,
        newlyBroken: MutableList<String>
    ) {
        val kind = LuaToolKindRegistry.findById(tool.kindId)
        if (kind == null) {
            LOG.warn("Skipping tool ${tool.id}: unknown kind '${tool.kindId}'")
            return
        }
        val checked = LuaToolHealthChecker.check(tool, kind)
        val result = applyEnvReasonOverride(tool, checked, deadRoots)
        val previousUsable = tool.isUsable
        LuaToolchainRegistry.getInstance()
            .updateToolCheck(tool.id, result.health, result.version, result.luaVersion, result.runtime)
        val newUsable = isResultUsable(result)
        if (previousUsable && !newUsable) newlyBroken.add(kind.displayName)
    }

    private fun applyEnvReasonOverride(
        tool: LuaRegisteredTool,
        result: LuaToolCheckResult,
        deadRoots: List<LuaEnvironmentState>
    ): LuaToolCheckResult {
        val envId = tool.environmentId ?: return result
        if (result.health.fileExists) return result
        val deadRoot = deadRoots.firstOrNull { it.id == envId } ?: return result
        val overridden = result.health.copy(reason = "Environment root missing: ${deadRoot.rootDir}")
        return result.copy(health = overridden)
    }

    private fun collectDeadEnvNotifications(
        envs: List<LuaEnvironmentState>,
        deadRoots: List<LuaEnvironmentState>
    ): List<LuaEnvironmentState> {
        synchronized(notifiedDeletedEnvIds) {
            val liveIds = (envs - deadRoots.toSet()).map { it.id }.toSet()
            notifiedDeletedEnvIds.removeAll(liveIds)
            val fresh = deadRoots.filter { it.id !in notifiedDeletedEnvIds }
            notifiedDeletedEnvIds.addAll(fresh.map { it.id })
            return fresh
        }
    }

    private fun marshalUiUpdates(
        newlyBroken: List<String>,
        deadEnvs: List<LuaEnvironmentState>
    ) {
        ApplicationManager.getApplication().invokeLater {
            EditorNotifications.getInstance(project).updateAllNotifications()
            if (newlyBroken.isNotEmpty()) notifyBrokenTools(newlyBroken)
            deadEnvs.forEach { notifyDeletedEnv(it) }
        }
    }

    private fun notifyBrokenTools(displayNames: List<String>) {
        val names = displayNames.joinToString(", ")
        warnBalloon(
            "Lua tool(s) became unavailable: $names. " +
                "Check Settings > Languages & Frameworks > Lua > Toolchain."
        )
    }

    private fun notifyDeletedEnv(env: LuaEnvironmentState) {
        warnBalloon(
            "Lua environment '${env.name}' was deleted from disk (${env.rootDir}). " +
                "Its tools are unavailable."
        )
    }

    private fun warnBalloon(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, NotificationType.WARNING)
            .notify(project)
    }

    private fun refreshBannersOnEdt() {
        ApplicationManager.getApplication().invokeLater {
            EditorNotifications.getInstance(project).updateAllNotifications()
        }
    }

    private fun rebuildWatchSet() {
        watchSet = buildWatchSet()
    }

    @org.jetbrains.annotations.TestOnly
    fun rebuildWatchSetNow() = rebuildWatchSet()

    @org.jetbrains.annotations.TestOnly
    fun prepareChangeNow(events: List<VFileEvent>): Boolean =
        HealthFileListener().prepareChange(events) != null

    private fun buildWatchSet(): LuaHealthWatchSet {
        val exactPaths = LuaToolchainRegistry.getInstance().tools()
            .map { canonicalize(it.path) }.toSet()
        val envRoots = LuaToolchainProjectSettings.getInstance(project).environments()
            .map { canonicalize(it.rootDir) }.toSet()
        val binDirs = envRoots.map { "$it/bin" }.toSet()
        return LuaHealthWatchSet(exactPaths, envRoots, binDirs)
    }

    private fun canonicalize(rawPath: String): String =
        runCatching { File(rawPath).canonicalPath }.getOrDefault(rawPath)

    override fun dispose() {
        Disposer.dispose(revalidationQueue)
    }

    private inner class HealthFileListener : AsyncFileListener {
        override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
            val currentWatchSet = watchSet
            val matched = events.any { matchesWatchedEvent(it, currentWatchSet) }
            if (!matched) return null
            return object : AsyncFileListener.ChangeApplier {
                override fun afterVfsChange() {
                    scheduleRevalidation()
                }
            }
        }
    }

    companion object {
        fun getInstance(project: Project): LuaToolHealthMonitor = project.service()
    }
}

private fun isResultUsable(result: LuaToolCheckResult): Boolean =
    result.health.fileExists && result.health.executable && result.health.probeOk != false

private class RevalidationOutcome {
    val newlyBroken: MutableList<String> = mutableListOf()
}
