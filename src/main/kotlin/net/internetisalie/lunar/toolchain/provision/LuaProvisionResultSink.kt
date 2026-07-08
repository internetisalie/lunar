package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import java.util.UUID

/**
 * The successful-provision registration parameters (design §3.1 step 10), bundled so the sink's
 * [LuaProvisionResultSink.register] stays a two-argument function (engineering-contract §2.1).
 */
data class LuaProvisionResult(
    val environmentId: String,
    val environmentName: String,
    val rootDir: String,
    val components: List<LuaProvisionedComponent>,
)

/**
 * Registers a successful provision's tools + environment (design §3.1 step 10). Extracted as a
 * seam so the orchestrator pipeline can be unit-tested with a spy instead of mutating the real
 * application registry / project settings.
 */
interface LuaProvisionResultSink {
    fun register(project: Project, result: LuaProvisionResult)
}

/**
 * Production sink: one [LuaRegisteredTool] per component's primary binary (each component's
 * `luac`/extra binaries ride the same tool record as extra paths — they are not separate tools),
 * registered PROVISIONED against the environment id, then the environment is upserted and
 * activated via TOOLING-02. Re-registration is idempotent, so a skip-all run may re-run it.
 */
class RegistryProvisionResultSink : LuaProvisionResultSink {
    override fun register(project: Project, result: LuaProvisionResult) {
        val registry = LuaToolchainRegistry.getInstance()
        val toolIds = result.components.map { component ->
            val toolId = UUID.randomUUID().toString()
            registry.registerProvisioned(toolFor(component, result.environmentId, toolId))
            toolId
        }
        LuaToolchainProjectSettings.getInstance(project).upsertEnvironmentAndActivate(
            LuaEnvironmentState(result.environmentId, result.environmentName, result.rootDir, toolIds.toMutableList()),
        )
    }

    private fun toolFor(component: LuaProvisionedComponent, environmentId: String, toolId: String): LuaRegisteredTool =
        LuaRegisteredTool(
            id = toolId,
            kindId = component.kindId,
            path = component.primaryBinary.toString(),
            version = component.resolvedVersion,
            luaVersion = null,
            runtime = null,
            origin = Origin.PROVISIONED,
            environmentId = environmentId,
            health = LuaToolHealth(fileExists = true, executable = true, probeOk = null, probedAtMtime = null, reason = null),
        )
}
