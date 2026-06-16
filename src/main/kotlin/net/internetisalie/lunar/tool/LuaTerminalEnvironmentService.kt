package net.internetisalie.lunar.tool

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.settings.LuaSettingsChangedListener
import java.nio.file.Path

/**
 * Project-level single source of truth for the Lua tool directories that must be injected into
 * the project's execution environment (TOOL-02). Both consumers read from here:
 *
 *  - the integrated terminal, via
 *    [net.internetisalie.lunar.tool.terminal.LuaShellExecOptionsCustomizer], and
 *  - Lua run/exec command lines, via [LuaToolEnvironment.prependToolDirsToPath]
 *    (called from `command/LuaCommandLine.kt`).
 *
 * Reconciliation note (Wave 10 grounding audit, fix #3): the two contradictory
 * `LuaTerminalEnvironmentService` definitions in the design docs are collapsed into this one
 * project-level service. There is no application-level variant.
 *
 * **Caching & invalidation:** the resolved directory list is cached and invalidated whenever Lua
 * settings change, via the *existing* [LuaSettingsChangedListener.TOPIC] (fix #2 — no invented
 * `LUA_SETTINGS_TOPIC`). Tool registration/binding changes publish that topic, so the cache stays
 * coherent without polling or TTLs.
 *
 * **Threading:** [getToolDirectories] is safe on any thread (notably the terminal customizer's
 * background thread, which holds no read action); it performs no PSI/VFS access. The cache field is
 * `@Volatile` for cross-thread visibility.
 */
@Service(Service.Level.PROJECT)
class LuaTerminalEnvironmentService(private val project: Project) {

    private val log = logger<LuaTerminalEnvironmentService>()

    @Volatile
    private var cachedToolDirectories: List<Path>? = null

    init {
        project.messageBus.connect().subscribe(
            LuaSettingsChangedListener.TOPIC,
            object : LuaSettingsChangedListener {
                override fun onSettingsChanged() {
                    cachedToolDirectories = null
                }
            },
        )
    }

    /**
     * Returns the de-duplicated, order-preserving list of directories containing the project's
     * effective Lua tool binaries. Project bindings take precedence over global defaults
     * (see [LuaToolManager.getEffectiveTool]). The first list entry is the highest priority and
     * should end up first on PATH.
     */
    fun getToolDirectories(): List<Path> {
        cachedToolDirectories?.let { return it }

        val directories = LuaToolManager.getInstance()
            .getAllValidTools(project)
            .asSequence()
            .mapNotNull { tool -> tool.path.takeIf { it.isNotBlank() } }
            .mapNotNull { runCatching { Path.of(it).parent }.getOrNull() }
            .distinct()
            .toList()

        cachedToolDirectories = directories
        if (directories.isEmpty()) {
            log.debug("No Lua tool directories resolved for project ${project.name}")
        }
        return directories
    }

    /** Drops the cached directory list; the next [getToolDirectories] recomputes it. */
    fun invalidate() {
        cachedToolDirectories = null
    }

    companion object {
        fun getInstance(project: Project): LuaTerminalEnvironmentService = project.service()
    }
}
