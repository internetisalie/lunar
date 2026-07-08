package net.internetisalie.lunar.toolchain.resolve

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.model.isUsable
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry

private const val RUNTIME_KIND_ID = "runtime-capability"

@Service(Service.Level.APP)
class LuaToolResolver {

    private val registry: LuaToolchainRegistry
        get() = LuaToolchainRegistry.getInstance()

    fun resolve(project: Project?, kindId: String): LuaRegisteredTool? =
        (resolveDetailed(project, kindId) as? LuaToolResolution.Resolved)?.tool

    fun resolveDetailed(project: Project?, kindId: String): LuaToolResolution {
        val trace = SkipTrace()
        val matches: (LuaRegisteredTool) -> Boolean = { it.kindId == kindId }
        val settings = project?.let { LuaToolchainProjectSettings.getInstance(it) }

        settings?.activeEnvironment()?.let { environment ->
            tierEnvironment(environment, matches, trace)?.let {
                return LuaToolResolution.Resolved(it, ResolutionSource.ACTIVE_ENVIRONMENT)
            }
        }
        settings?.binding(kindId)?.let { boundId ->
            tierBound(boundId, BoundTier(ResolutionSource.PROJECT_BINDING, kindId), trace)?.let {
                return LuaToolResolution.Resolved(it, ResolutionSource.PROJECT_BINDING)
            }
        }
        registry.globalBindings()[kindId]?.let { boundId ->
            tierBound(boundId, BoundTier(ResolutionSource.GLOBAL_BINDING, kindId), trace)?.let {
                return LuaToolResolution.Resolved(it, ResolutionSource.GLOBAL_BINDING)
            }
        }
        inventoryFallback(matches)?.let {
            return LuaToolResolution.Resolved(it, ResolutionSource.INVENTORY_FALLBACK)
        }
        return LuaToolResolution.Unresolved(kindId, trace.entries())
    }

    fun resolveIn(environment: LuaEnvironmentState, kindId: String): LuaRegisteredTool? =
        tierEnvironment(environment, { it.kindId == kindId }, SkipTrace())

    fun resolveRuntime(project: Project?): LuaRegisteredTool? =
        (resolveRuntimeDetailed(project) as? LuaToolResolution.Resolved)?.tool

    fun resolveRuntimeDetailed(project: Project?): LuaToolResolution {
        val trace = SkipTrace()
        val runtimeKindIds = runtimeKinds().map { it.id }.toSet()
        val matches: (LuaRegisteredTool) -> Boolean = { it.kindId in runtimeKindIds }
        val settings = project?.let { LuaToolchainProjectSettings.getInstance(it) }

        settings?.activeEnvironment()?.let { environment ->
            tierEnvironment(environment, matches, trace)?.let {
                return LuaToolResolution.Resolved(it, ResolutionSource.ACTIVE_ENVIRONMENT)
            }
        }
        resolveBoundRuntime(ResolutionSource.PROJECT_BINDING, { settings?.binding(it) }, trace)?.let { return it }
        resolveBoundRuntime(ResolutionSource.GLOBAL_BINDING, { registry.globalBindings()[it] }, trace)?.let {
            return it
        }
        for (kind in runtimeKinds()) {
            inventoryFallback { it.kindId == kind.id }?.let {
                return LuaToolResolution.Resolved(it, ResolutionSource.INVENTORY_FALLBACK)
            }
        }
        return LuaToolResolution.Unresolved(RUNTIME_KIND_ID, trace.entries())
    }

    fun resolveAll(project: Project?): Map<String, LuaRegisteredTool> =
        LuaToolKindRegistry.all().mapNotNull { kind ->
            resolve(project, kind.id)?.let { kind.id to it }
        }.toMap()

    fun notConfiguredMessage(kindId: String): String {
        val displayName = LuaToolKindRegistry.findById(kindId)?.displayName ?: kindId
        return "No usable $displayName configured. Add or bind one under " +
            "Settings | Languages & Frameworks | Lua | Toolchain."
    }

    private fun tierEnvironment(
        environment: LuaEnvironmentState,
        matches: (LuaRegisteredTool) -> Boolean,
        trace: SkipTrace
    ): LuaRegisteredTool? {
        for (toolId in environment.toolIds) {
            val tool = registry.tool(toolId)
            if (tool == null) {
                trace.record(ResolutionSource.ACTIVE_ENVIRONMENT, toolId, SkipReason.NOT_IN_INVENTORY)
                continue
            }
            if (!matches(tool)) continue
            if (!tool.isUsable) {
                trace.record(ResolutionSource.ACTIVE_ENVIRONMENT, toolId, SkipReason.UNUSABLE)
                continue
            }
            return tool
        }
        return null
    }

    private fun tierBound(boundId: String, bound: BoundTier, trace: SkipTrace): LuaRegisteredTool? {
        val tool = registry.tool(boundId)
        val reason = when {
            tool == null -> SkipReason.NOT_IN_INVENTORY
            tool.kindId != bound.expectedKindId -> SkipReason.WRONG_KIND
            !tool.isUsable -> SkipReason.UNUSABLE
            else -> return tool
        }
        trace.record(bound.tier, boundId, reason)
        return null
    }

    private fun resolveBoundRuntime(
        tier: ResolutionSource,
        boundIdOf: (String) -> String?,
        trace: SkipTrace
    ): LuaToolResolution.Resolved? {
        for (kind in runtimeKinds()) {
            val boundId = boundIdOf(kind.id) ?: continue
            val tool = tierBound(boundId, BoundTier(tier, kind.id), trace)
            if (tool != null) return LuaToolResolution.Resolved(tool, tier)
        }
        return null
    }

    private fun runtimeKinds(): List<LuaToolKind> = LuaToolKindRegistry.all().filter { it.isRuntime }

    private fun inventoryFallback(matches: (LuaRegisteredTool) -> Boolean): LuaRegisteredTool? =
        registry.tools().firstOrNull { matches(it) && it.isUsable }

    private data class BoundTier(val tier: ResolutionSource, val expectedKindId: String)

    private class SkipTrace {
        private val skipped = mutableListOf<SkippedBinding>()

        fun record(tier: ResolutionSource, toolId: String, reason: SkipReason) {
            skipped += SkippedBinding(tier, toolId, reason)
        }

        fun entries(): List<SkippedBinding> = skipped.toList()
    }

    companion object {
        fun getInstance(): LuaToolResolver =
            ApplicationManager.getApplication().getService(LuaToolResolver::class.java)
    }
}
