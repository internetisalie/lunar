package net.internetisalie.lunar.toolchain.discovery

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import java.io.File
import java.util.UUID

/**
 * Turns a detected env directory into registered tools plus an active environment record
 * (TOOLING-02-14, design §2.7/§3.7). Runs on a background thread: `registerTool` probes each
 * binary and asserts a non-dispatch thread.
 */
object LuaEnvironmentAdopter {

    /** Registers every kind binary found in [directory], then upserts+activates the env; `null` if none. */
    fun adopt(project: Project, directory: String): LuaEnvironmentState? {
        val environmentId = UUID.randomUUID().toString()
        val toolIds = mutableListOf<String>()
        for (kind in LuaToolKindRegistry.all()) {
            ProgressManager.checkCanceled()
            registerKindBinary(kind, directory, environmentId)?.let { toolIds.add(it) }
        }
        if (toolIds.isEmpty()) return null
        return LuaToolchainProjectSettings.getInstance(project).upsertEnvironmentAndActivate(
            LuaEnvironmentState(
                id = environmentId,
                name = File(directory).name,
                rootDir = directory,
                toolIds = toolIds
            )
        )
    }

    private fun registerKindBinary(kind: LuaToolKind, directory: String, environmentId: String): String? {
        for (base in kind.binaryNames) {
            for (candidate in candidatePaths(directory, base)) {
                if (!isExecutableFile(candidate)) continue
                val registered = LuaToolchainRegistry.getInstance()
                    .registerTool(candidate, kind.id, Origin.DISCOVERED, environmentId)
                if (registered != null) return registered.id
            }
        }
        return null
    }

    private fun candidatePaths(directory: String, base: String): List<String> = listOf(
        "$directory/bin/$base",
        "$directory/$base.exe",
        "$directory/$base.bat"
    )

    private fun isExecutableFile(path: String): Boolean {
        val file = File(path)
        return file.isFile && file.canExecute()
    }
}
