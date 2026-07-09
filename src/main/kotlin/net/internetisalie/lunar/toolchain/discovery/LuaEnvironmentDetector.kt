package net.internetisalie.lunar.toolchain.discovery

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.toolchain.model.Capability
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings

/**
 * Pure detection of env-shaped directories (TOOLING-02-14, design §2.6/§3.6). Generalizes the
 * legacy lua+luarocks env heuristic to kind descriptors: a directory is "env-shaped" when it holds
 * a RUNTIME-capability binary and a PACKAGE_MANAGER-capability binary.
 */
object LuaEnvironmentDetector {

    val CONVENTIONAL_NAMES: List<String> = listOf(".lua", "lua_env", "_lua")

    /** Path of the first env-shaped directory, or `null`. VFS reads run inside a read action. */
    fun detect(project: Project): String? = ApplicationManager.getApplication().runReadAction<String?> {
        val base = project.guessProjectDir() ?: return@runReadAction null
        val candidates = base.children.filter { it.isDirectory } +
            CONVENTIONAL_NAMES.mapNotNull { base.findChild(it) }
        candidates.distinct().firstOrNull { isEnvShaped(it) }?.path
    }

    fun isEnvShaped(dir: VirtualFile): Boolean =
        hasCapabilityBinary(dir, Capability.RUNTIME) && hasCapabilityBinary(dir, Capability.PACKAGE_MANAGER)

    /** Whether [directory] matches any recorded environment by normalized absolute path. */
    fun isKnownDirectory(project: Project, directory: String): Boolean {
        val target = LuaToolchainProjectSettings.normalizeDir(directory)
        return LuaToolchainProjectSettings.getInstance(project).environments()
            .any { LuaToolchainProjectSettings.normalizeDir(it.rootDir) == target }
    }

    private fun hasCapabilityBinary(dir: VirtualFile, capability: Capability): Boolean {
        val kinds = LuaToolKindRegistry.all().filter { capability in it.capabilities }
        return kinds.any { kind -> kind.binaryNames.any { hasBinary(dir, it) } }
    }

    private fun hasBinary(dir: VirtualFile, base: String): Boolean =
        dir.findFileByRelativePath("bin/$base") != null ||
            dir.findChild("$base.exe") != null ||
            dir.findChild("$base.bat") != null
}
