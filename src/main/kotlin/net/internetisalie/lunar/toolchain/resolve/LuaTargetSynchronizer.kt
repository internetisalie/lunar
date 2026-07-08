package net.internetisalie.lunar.toolchain.resolve

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaRuntimeInfo
import net.internetisalie.lunar.toolchain.registry.LuaToolchainChange
import net.internetisalie.lunar.toolchain.registry.LuaToolchainEvent
import net.internetisalie.lunar.toolchain.registry.LuaToolchainListener

private val LOG = logger<LuaTargetSynchronizer>()

private val UNINITIALIZED = " uninitialized"

private val RUNTIME_AFFECTING = setOf(
    LuaToolchainChange.TOOL_REGISTERED,
    LuaToolchainChange.TOOL_UPDATED,
    LuaToolchainChange.TOOL_REMOVED,
    LuaToolchainChange.GLOBAL_BINDING_CHANGED,
    LuaToolchainChange.PROJECT_BINDING_CHANGED,
    LuaToolchainChange.ENVIRONMENT_UPDATED,
    LuaToolchainChange.ENVIRONMENT_REMOVED,
    LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED
)

@Service(Service.Level.PROJECT)
class LuaTargetSynchronizer(private val project: Project) : Disposable {

    @Volatile
    private var lastAppliedRuntimeId: String? = UNINITIALIZED

    init {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            LuaToolchainListener.TOPIC,
            object : LuaToolchainListener {
                override fun toolchainChanged(event: LuaToolchainEvent) = onEvent(event)
            }
        )
    }

    fun ensureSynchronized() = recompute()

    @org.jetbrains.annotations.TestOnly
    internal fun resetGuardForTest() {
        lastAppliedRuntimeId = UNINITIALIZED
    }

    internal fun onEvent(event: LuaToolchainEvent) {
        if (event.project != null && event.project != project) return
        if (event.change !in RUNTIME_AFFECTING) return
        recompute()
    }

    private fun recompute() {
        try {
            val resolved = effectiveRuntimeTool()
            val newId = resolved?.id
            if (newId == lastAppliedRuntimeId) return
            lastAppliedRuntimeId = newId
            val runtime = resolved?.runtime ?: return
            applyTarget(targetFor(runtime))
        } catch (throwable: Throwable) {
            LOG.warn("Failed to synchronize target from effective runtime", throwable)
        }
    }

    private fun effectiveRuntimeTool(): LuaRegisteredTool? {
        val resolution = LuaToolResolver.getInstance().resolveRuntimeDetailed(project)
        if (resolution !is LuaToolResolution.Resolved) return null
        if (resolution.source == ResolutionSource.INVENTORY_FALLBACK) return null
        return resolution.tool
    }

    private fun targetFor(info: LuaRuntimeInfo): Target {
        val label = Regex("""(\d+\.\d+)""").find(info.version)?.groupValues?.get(1) ?: info.version
        return PlatformVersionRegistry.resolveTarget(info.platform, label)
    }

    private fun applyTarget(target: Target) {
        ApplicationManager.getApplication().invokeLater {
            val settings = LuaProjectSettings.getInstance(project)
            if (settings.state.getTarget() == target) return@invokeLater
            val previousLevel = settings.state.languageLevel
            settings.setTargetAndNotify(target)
            if (settings.state.languageLevel != previousLevel) PlatformLibraryIndex.reload()
        }
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): LuaTargetSynchronizer =
            project.getService(LuaTargetSynchronizer::class.java)
    }
}
