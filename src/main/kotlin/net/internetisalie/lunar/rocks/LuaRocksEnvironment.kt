package net.internetisalie.lunar.rocks

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.run.LuaRocksSettings
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.tool.LuaToolManager
import net.internetisalie.lunar.tool.LuaToolType

/**
 * Stateless resolver for the effective LuaRocks executable and registry server (ROCKS-06).
 *
 * All methods are pure settings reads — EDT-safe; no I/O, no subprocess invocations.
 * Callers are responsible for running any resulting command on a background thread.
 *
 * Precedence for server: project [LuaProjectSettings.State.rocksServerUrl] (non-blank)
 *   > app [LuaRocksSettings.serverUrl] (non-blank) > `null` (no `--server` emitted).
 *
 * Precedence for executable: TOOL-02 project binding ([LuaToolManager.getEffectiveTool])
 *   > [LuaRocksSettings.executablePath] (app fallback, default `"luarocks"`).
 */
object LuaRocksEnvironment {

    /**
     * Returns the effective registry server URL for [project], or `null` if nothing is configured.
     * A `null` result means callers must NOT append `--server`; the luarocks CLI falls back to
     * its own default (luarocks.org). This preserves pre-ROCKS-06 behavior exactly.
     *
     * Resolution order:
     * 1. Project override ([LuaProjectSettings.State.rocksServerUrl]) if non-blank.
     * 2. App default ([LuaRocksSettings.serverUrl]) if non-blank.
     * 3. `null`.
     */
    fun resolveServer(project: Project?): String? {
        if (project != null) {
            val projectUrl = LuaProjectSettings.getInstance(project).state.rocksServerUrl.trim()
            if (projectUrl.isNotBlank()) return projectUrl
        }
        val appUrl = LuaRocksSettings.getInstance().serverUrl.trim()
        return if (appUrl.isNotBlank()) appUrl else null
    }

    /**
     * Returns the effective `luarocks` binary path for [project].
     *
     * Resolution order:
     * 1. TOOL-02 project binding via [LuaToolManager.getEffectiveTool] (non-blank path).
     * 2. [LuaRocksSettings.executablePath] (app setting, default `"luarocks"`).
     */
    fun resolveExecutable(project: Project?): String {
        if (project != null) {
            val bound = LuaToolManager.getInstance()
                .getEffectiveTool(project, LuaToolType.LUAROCKS)
                ?.path
                ?.takeIf { it.isNotBlank() }
            if (bound != null) return bound
        }
        return LuaRocksSettings.getInstance().executablePath
    }

    /**
     * Returns [args] with `["--server", server]` prepended after the executable when [server]
     * is non-null and non-blank; otherwise returns [args] unchanged.
     *
     * Note: `--server` is a luarocks **global** flag and must appear before the subcommand.
     * [args] is expected to start with the subcommand (e.g. `["search", "--porcelain", "x"]`).
     * The caller holds the executable separately; `--server` is injected at index 0 of args.
     */
    fun withServer(args: List<String>, server: String?): List<String> =
        if (server.isNullOrBlank()) args else listOf("--server", server) + args
}
