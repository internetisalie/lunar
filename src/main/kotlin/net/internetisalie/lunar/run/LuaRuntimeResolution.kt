package net.internetisalie.lunar.run

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver
import java.io.File
import java.util.UUID

private const val RUNTIME_KIND_ID = "lua"

/**
 * TOOLING-05 §3.2. Resolves the RUNTIME tool a run/test configuration should launch:
 * an explicit stored path always wins (registry hit → that tool, miss → an ad-hoc RUNTIME tool),
 * otherwise the project-resolved default. Shared by the run and test configurations.
 */
fun resolveConfiguredRuntime(project: Project, storedPath: String?): LuaRegisteredTool? {
    val path = storedPath?.takeIf { it.isNotEmpty() }
        ?: return LuaToolResolver.getInstance().resolveRuntime(project)
    return LuaToolchainRegistry.getInstance().findByPath(path) ?: adHocRuntime(path)
}

/** Builds the [LuaRegisteredTool] wrapper for an explicit, non-registered interpreter path. */
fun adHocRuntime(path: String): LuaRegisteredTool {
    val file = File(path)
    return LuaRegisteredTool(
        id = UUID.randomUUID().toString(),
        kindId = RUNTIME_KIND_ID,
        path = path,
        version = null,
        luaVersion = null,
        runtime = null,
        origin = Origin.MANUAL,
        environmentId = null,
        health = LuaToolHealth(
            fileExists = file.exists(),
            executable = file.canExecute(),
            probeOk = null,
            probedAtMtime = null,
            reason = null
        )
    )
}
