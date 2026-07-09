package net.internetisalie.lunar.rocks

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver

/**
 * Stateless facade for the effective LuaRocks executable and registry server (ROCKS-06;
 * cut over to the TOOLING-01/02 toolchain stack in TOOLING-05 Phase 2).
 *
 * All methods are pure state reads — EDT-safe; no I/O, no subprocess invocations.
 * Callers are responsible for running any resulting command on a background thread.
 *
 * Precedence for server: project [LuaProjectSettings.State.rocksServerUrl] (non-blank)
 *   > app default ([LuaToolchainRegistry.kindOption] for [LuaKindOptionKeys.LUAROCKS_SERVER_URL],
 *   non-blank) > `null` (no `--server` emitted).
 *
 * Precedence for executable: the resolved `luarocks` tool
 *   ([LuaToolResolver.resolve]) → `null` (no hardcoded default; contract §3 step 5).
 */
object LuaRocksEnvironment {

    /**
     * Returns the effective registry server URL for [project], or `null` if nothing is configured.
     * A `null` result means callers must NOT append `--server`; the luarocks CLI falls back to
     * its own default (luarocks.org). This preserves pre-ROCKS-06 behavior exactly.
     *
     * Resolution order:
     * 1. Project override ([LuaProjectSettings.State.rocksServerUrl]) if non-blank.
     * 2. App default (TOOLING-02 [LuaKindOptionKeys.LUAROCKS_SERVER_URL] option) if non-blank.
     * 3. `null`.
     */
    fun resolveServer(project: Project?): String? {
        if (project != null) {
            val projectUrl = LuaProjectSettings.getInstance(project).state.rocksServerUrl.trim()
            if (projectUrl.isNotBlank()) return projectUrl
        }
        val appUrl = LuaToolchainRegistry.getInstance()
            .kindOption(LuaKindOptionKeys.LUAROCKS_SERVER_URL)
            .trim()
        return if (appUrl.isNotBlank()) appUrl else null
    }

    /**
     * Returns the resolved `luarocks` binary path for [project], or `null` when nothing usable
     * resolves. There is no hardcoded default (contract §3 step 5); a `null` result surfaces a
     * kind-specific configure hint at each call site (design §3.3).
     */
    fun resolveExecutable(project: Project?): String? =
        LuaToolResolver.getInstance().resolve(project, "luarocks")?.path

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
