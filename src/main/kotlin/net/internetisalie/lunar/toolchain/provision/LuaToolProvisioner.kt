package net.internetisalie.lunar.toolchain.provision

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Native-provisioning orchestrator (design §2.2, §3.1). Validates the request, serializes per
 * canonical rootDir, and runs the pipeline ([LuaProvisionEngine]) on a [Task.Backgroundable];
 * results register through the pipeline's [LuaProvisionResultSink].
 *
 * `@Service(Service.Level.APP)`, annotation-registered (no `plugin.xml` entry). Holds no
 * `Project` field — the project is a per-call parameter (engineering-contract §11); `provision`
 * may be called from the EDT but all real work runs inside the background task.
 */
@Service(Service.Level.APP)
class LuaToolProvisioner {
    private val reserved = ConcurrentHashMap.newKeySet<String>()

    /** Validates + serializes + queues the background provisioning of [request] (design §3.1). */
    fun provision(project: Project, request: LuaProvisionRequest) {
        val canonical = validate(request)
        val canonicalRequest = request.copy(rootDir = canonical)
        if (!tryReserve(canonical)) {
            notify(project, "Provisioning already in progress for $canonical", NotificationType.WARNING)
            return
        }
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Provisioning Lua toolchain", true) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        LuaProvisionEngine().execute(LuaProvisionJob(project, canonicalRequest, indicator))
                    } finally {
                        release(canonical)
                    }
                }
            },
        )
    }

    /** §3.1 step 1: name non-blank, rootDir canonicalized + free of `"`/`;`, items non-empty. */
    private fun validate(request: LuaProvisionRequest): String {
        if (request.environmentName.isBlank()) {
            throw LuaProvisionException("Environment name must not be blank.")
        }
        if (request.items.isEmpty()) {
            throw LuaProvisionException("A provisioning request must include at least one tool.")
        }
        val canonical = FileUtil.toCanonicalPath(File(request.rootDir).absolutePath)
        if (canonical.contains('"') || canonical.contains(';')) {
            throw LuaProvisionException("Environment directory must not contain '\"' or ';': $canonical")
        }
        return canonical
    }

    /** Refuses a second concurrent provision for [rootDir] (design §3.1 step 2). Test seam. */
    internal fun tryReserve(rootDir: String): Boolean = reserved.add(rootDir)

    internal fun release(rootDir: String) {
        reserved.remove(rootDir)
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        BalloonProvisionNotifier().notify(project, message, type)
    }

    companion object {
        fun getInstance(): LuaToolProvisioner =
            ApplicationManager.getApplication().getService(LuaToolProvisioner::class.java)
    }
}
