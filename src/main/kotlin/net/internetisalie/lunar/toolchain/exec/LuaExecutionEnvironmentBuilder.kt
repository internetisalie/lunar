package net.internetisalie.lunar.toolchain.exec

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.RockspecRunPathProvider
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import net.internetisalie.lunar.toolchain.registry.LuaToolchainEvent
import net.internetisalie.lunar.toolchain.registry.LuaToolchainListener
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver
import java.io.File
import java.nio.file.Path

/**
 * Computes the [LuaLaunchEnvironment] for a project — the single source of truth for the
 * PATH-prepend directories and the `LUA_PATH` / `LUA_CPATH` / `LUAROCKS_CONFIG` assembly
 * (TOOLING-03-07/08/09/14/16). Replaces `LuaTerminalEnvironmentService` + `LuaToolEnvironment`
 * and the LUA_PATH unions duplicated across the run configs.
 *
 * **Caching:** only [pathPrependDirs] is cached (resolver-derived; changes exactly when the
 * toolchain changes). It is invalidated by the app-level `LuaToolchainListener.TOPIC` — fixing the
 * stale-cache defect where a `registerTool` fired no event. `LUA_PATH` / `LUA_CPATH` are **not**
 * cached here: they depend on rockspec PSI, which `RockspecSourcePathProvider` already caches under
 * `PsiModificationTracker`; double-caching would reintroduce invalidation bugs.
 *
 * **Threading:** [pathPrependDirs] is safe on any thread (no PSI/VFS — the terminal customizer's
 * background-thread contract); [build] reaches PSI only through `RockspecSourcePathProvider`, which
 * is EDT-safe by construction.
 */
@Service(Service.Level.PROJECT)
class LuaExecutionEnvironmentBuilder(private val project: Project) : Disposable {

    @Volatile
    private var cachedPathPrependDirs: List<Path>? = null

    init {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            LuaToolchainListener.TOPIC,
            object : LuaToolchainListener {
                override fun toolchainChanged(event: LuaToolchainEvent) = invalidate()
            },
        )
    }

    /** TOOLING-03-07/11. Cached resolver-derived PATH prepend dirs, highest priority first. */
    fun pathPrependDirs(): List<Path> {
        cachedPathPrependDirs?.let { return it }

        val resolver = LuaToolResolver.getInstance()
        val dirs = LuaToolKindRegistry.all()
            .mapNotNull { resolver.resolve(project, it.id) }
            .mapNotNull { tool -> tool.path.takeIf { it.isNotBlank() } }
            .mapNotNull { runCatching { Path.of(it).parent }.getOrNull() }
            .distinct()

        cachedPathPrependDirs = dirs
        return dirs
    }

    /** TOOLING-03-08/09/14. Full environment; [sourcePathOverride] = run-config sourcePath. */
    fun build(sourcePathOverride: String? = null): LuaLaunchEnvironment {
        if (sourcePathOverride?.isNotEmpty() == true) {
            return LuaLaunchEnvironment(
                pathPrependDirs = pathPrependDirs(),
                luaPath = sourcePathOverride,
                luaCPath = null,
                luarocksConfig = computeLuarocksConfig(),
            )
        }

        val prefix = RockspecRunPathProvider.luaPathPrefix(project)
        val projectPath = LuaProjectSettings.getInstance(project).state.expandSourcePath(project)
        val union = (prefix + projectPath).trimEnd(';') + ";;"

        return LuaLaunchEnvironment(
            pathPrependDirs = pathPrependDirs(),
            luaPath = if (union == ";;") null else union,
            luaCPath = RockspecRunPathProvider.luaCPath(project),
            luarocksConfig = computeLuarocksConfig(),
        )
    }

    private fun computeLuarocksConfig(): String? {
        val environment = LuaToolchainProjectSettings.getInstance(project).activeEnvironment()
            ?: return null
        val configFile = File(environment.rootDir, "luarocks-config.lua")
        return if (configFile.isFile) configFile.path else null
    }

    /** Drops the cached dir list; the next [pathPrependDirs] recomputes it. */
    fun invalidate() {
        cachedPathPrependDirs = null
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): LuaExecutionEnvironmentBuilder = project.service()

        fun forProject(project: Project): LuaLaunchEnvironment = getInstance(project).build()
    }
}
