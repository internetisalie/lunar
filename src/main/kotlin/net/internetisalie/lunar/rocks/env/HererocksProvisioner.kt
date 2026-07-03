package net.internetisalie.lunar.rocks.env

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import net.internetisalie.lunar.util.LuaProcessUtil
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Provisions a hererocks environment (ROCKS-14-03/06/07) on a background task and binds it on
 * success (ROCKS-14-04). hererocks is invoked purely as an out-of-process provisioner; nothing in
 * the plugin runtime depends on it afterward.
 */
@Service(Service.Level.PROJECT)
class HererocksProvisioner(private val project: Project) {
    enum class Mode { CREATE, UPGRADE, RECREATE }

    private val active = ConcurrentHashMap.newKeySet<String>()

    /**
     * Provisions [spec] per [mode] on a [Task.Backgroundable]; binds via [HererocksEnvBinder] when
     * the process exits 0. Serialized per directory (ROCKS-14-09): a second request for a
     * directory already provisioning is refused with a balloon and no process is spawned.
     */
    fun provision(spec: HererocksEnvState, mode: Mode) {
        if (!active.add(spec.directory)) {
            notify("Provisioning already in progress for ${spec.directory}", NotificationType.WARNING)
            return
        }
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Provisioning Lua environment", true) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        runProvision(ProvisionRequest(spec, mode), indicator)
                    } finally {
                        active.remove(spec.directory)
                    }
                }
            },
        )
    }

    /** Bundles the per-directory provisioning inputs to keep helper arities within the tripwire. */
    private data class ProvisionRequest(val spec: HererocksEnvState, val mode: Mode)

    private fun runProvision(request: ProvisionRequest, indicator: ProgressIndicator) {
        indicator.checkCanceled()
        val prefix = HererocksLocator.resolvePrefix()
        if (prefix == null) {
            notify(HererocksLocator.REMEDIATION, NotificationType.ERROR)
            return
        }
        val spec = request.spec
        indicator.checkCanceled()
        if (request.mode == Mode.RECREATE) FileUtil.delete(File(spec.directory))
        val output = LuaProcessUtil.capture(GeneralCommandLine(argsFor(prefix, spec)), PROVISION_TIMEOUT_MS)
        if (output.exitCode == 0) {
            val bound = spec.copy(id = spec.id.ifBlank { UUID.randomUUID().toString() })
            HererocksEnvBinder.bind(project, bound)
        } else {
            val tail = output.stderr.trim().lines().takeLast(STDERR_TAIL).joinToString("\n").ifBlank { "(no output)" }
            LOG.warn("hererocks provisioning failed (exit ${output.exitCode}): $tail")
            notify("Lua environment provisioning failed: $tail", NotificationType.ERROR)
        }
    }

    /** Refuses [spec] when its directory is already provisioning (ROCKS-14-09). Test seam. */
    internal fun tryReserve(spec: HererocksEnvState): Boolean = active.add(spec.directory)

    internal fun release(spec: HererocksEnvState) {
        active.remove(spec.directory)
    }

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, type)
            .notify(project)
    }

    companion object {
        private val LOG = Logger.getInstance(HererocksProvisioner::class.java)
        private const val NOTIFICATION_GROUP = "notification.group.lunar.luarocks"
        private const val PROVISION_TIMEOUT_MS = 600_000
        private const val STDERR_TAIL = 20

        fun getInstance(project: Project): HererocksProvisioner =
            project.getService(HererocksProvisioner::class.java)

        /** Builds the hererocks argument list for [spec] (ROCKS-14-03/06/07, design §3.2). */
        fun argsFor(prefix: List<String>, spec: HererocksEnvState): List<String> {
            val flavorFlag = if (spec.flavor == HererocksFlavor.LUAJIT) {
                listOf("--luajit", spec.luaVersion)
            } else {
                listOf("--lua", spec.luaVersion)
            }
            return prefix + listOf(spec.directory) + flavorFlag + listOf("--luarocks", spec.luarocksVersion)
        }
    }
}
