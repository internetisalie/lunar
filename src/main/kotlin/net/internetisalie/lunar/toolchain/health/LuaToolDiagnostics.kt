package net.internetisalie.lunar.toolchain.health

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver

private val LOG: Logger = Logger.getInstance(LuaToolDiagnostics::class.java)

private const val PREFIX = "[TOOLCHAIN-DIAG]"

/**
 * Full-toolchain diagnostics snapshot (design §2.5 / §4.1). One log.info per line, prefix
 * [TOOLCHAIN-DIAG], greppable. Called automatically after every revalidation pass (design §3.3 step 6)
 * and on demand via [LuaToolchainDiagnosticsAction]. The [emit] sink defaults to [LOG]::info so
 * production calls use a single argument while TC-08 can capture lines without reading the IDE log.
 */
object LuaToolDiagnostics {

    fun logSnapshot(project: Project?, emit: (String) -> Unit = LOG::info) {
        val registry = LuaToolchainRegistry.getInstance()
        val tools = registry.tools()
        val globalBindings = registry.globalBindings()
        val projectSettings = project?.let { LuaToolchainProjectSettings.getInstance(it) }

        emitHeader(project, tools, projectSettings, emit)
        if (tools.isEmpty()) return

        tools.forEach { emitToolLine(it, emit) }
        emitBindingLines(globalBindings, "global", emit)

        if (project == null || projectSettings == null) return

        val projectBindings = projectSettings.getState().bindings.toMap()
        emitBindingLines(projectBindings, "project", emit)

        val envs = projectSettings.environments()
        val activeId = projectSettings.getState().activeEnvironmentId.takeIf { it.isNotBlank() }
        envs.forEach { emitEnvLine(it, activeId, emit) }

        emitResolveLines(project, emit)
    }

    private fun emitHeader(
        project: Project?,
        tools: List<LuaRegisteredTool>,
        projectSettings: LuaToolchainProjectSettings?,
        emit: (String) -> Unit
    ) {
        val kindCount = LuaToolKindRegistry.all().size
        val envCount = projectSettings?.environments()?.size ?: 0
        val projectLabel = project?.name ?: "-"
        emit("$PREFIX snapshot project='$projectLabel' kinds=$kindCount tools=${tools.size} envs=$envCount")
    }

    private fun emitToolLine(tool: LuaRegisteredTool, emit: (String) -> Unit) {
        val version = tool.version ?: "-"
        val env = tool.environmentId ?: "-"
        emit(
            "$PREFIX tool id=${tool.id.take(8)} kind=${tool.kindId} path=${tool.path} " +
                "origin=${tool.origin} version=$version env=$env " +
                "health=${formatHealth(tool.health)}"
        )
    }

    private fun emitBindingLines(bindings: Map<String, String>, scope: String, emit: (String) -> Unit) {
        bindings.entries.sortedBy { it.key }.forEach { (kindId, toolId) ->
            emit("$PREFIX binding scope=$scope kind=$kindId toolId=${toolId.take(8)}")
        }
    }

    private fun emitEnvLine(env: LuaEnvironmentState, activeId: String?, emit: (String) -> Unit) {
        val active = env.id == activeId
        val toolList = env.toolIds.joinToString(",") { it.take(8) }
        emit("$PREFIX env id=${env.id.take(8)} name='${env.name}' root=${env.rootDir} active=$active tools=[$toolList]")
    }

    private fun emitResolveLines(project: Project, emit: (String) -> Unit) {
        val resolver = LuaToolResolver.getInstance()
        LuaToolKindRegistry.all().sortedBy { it.id }.forEach { kind ->
            val tool = resolver.resolve(project, kind.id)
            if (tool != null) {
                emit("$PREFIX resolve kind=${kind.id} -> id=${tool.id.take(8)} path=${tool.path}")
            } else {
                emit("$PREFIX resolve kind=${kind.id} -> none")
            }
        }
    }

    private fun formatHealth(health: LuaToolHealth): String {
        val probe = when (health.probeOk) {
            true -> "true"
            false -> "false"
            null -> "-"
        }
        val mtime = health.probedAtMtime?.toString() ?: "-"
        val reason = health.reason?.let { "\"$it\"" } ?: "\"-\""
        return "[exists=${health.fileExists} exec=${health.executable} probe=$probe mtime=$mtime reason=$reason]"
    }
}
