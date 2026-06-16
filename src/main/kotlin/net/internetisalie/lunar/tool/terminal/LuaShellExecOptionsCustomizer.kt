package net.internetisalie.lunar.tool.terminal

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.tool.LuaTerminalEnvironmentService
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer

/**
 * TOOL-00-01 de-risking spike: terminal PATH injection.
 *
 * Source-verified API (intellij-community 2026.1):
 * - Interface: `org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer`
 *   (`@ApiStatus.Experimental`).
 * - EP id: `org.jetbrains.plugins.terminal.shellExecOptionsCustomizer` (dynamic="true").
 * - `customizeExecOptions(project, options)` runs on a **background thread without a read action**
 *   (`@RequiresBackgroundThread` / `@RequiresReadLockAbsence`).
 *
 * KEY CORRECTION over the original design draft: [MutableShellExecOptions.envs] is a **read-only**
 * `Map<String, String>` — there is NO `options.environment["PATH"] = ...` setter. PATH is mutated
 * through the dedicated, EEL-aware methods: [MutableShellExecOptions.prependEntryToPATH] (takes a
 * `java.nio.file.Path`), which inserts the entry first, joins with the *remote* path separator, and
 * translates local paths to the remote environment (WSL/Docker/SSH) automatically. This is the
 * idiom used by the platform's own `ShellExecOptionsCustomizerTest`.
 *
 * TOOL-02 wires this to the production project-level [LuaTerminalEnvironmentService], which owns
 * resolution (project binding > global default), caching, and settings-change invalidation. The
 * customizer simply prepends the resolved directories to PATH.
 */
class LuaShellExecOptionsCustomizer : ShellExecOptionsCustomizer {
    override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
        // Background thread, no read action — safe to resolve tools here.
        val toolDirs = LuaTerminalEnvironmentService.getInstance(project).getToolDirectories()
        // getToolDirectories() returns highest-priority first. prependEntryToPATH inserts each
        // entry at the front, so prepend in reverse to keep the first entry first on PATH.
        // It also handles the OS/remote path separator and local->remote path translation; do NOT
        // touch the read-only envs map directly.
        for (dir in toolDirs.asReversed()) {
            shellExecOptions.prependEntryToPATH(dir)
        }
    }
}
