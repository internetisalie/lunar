package net.internetisalie.lunar.tool.terminal

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.tool.LuaToolDescriptor
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
 * This spike proves the API compiles and the injection point works. TOOL-02 owns the production
 * service (`LuaTerminalEnvironmentService`), caching, and settings-change invalidation; the
 * customizer simply delegates tool-dir resolution there.
 */
class LuaShellExecOptionsCustomizer : ShellExecOptionsCustomizer {
    override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
        // Background thread, no read action — safe to resolve tools off PATH here.
        for (descriptor in LuaToolDescriptor.DESCRIPTORS) {
            val binary = descriptor.resolveOnPath() ?: continue
            val toolDir = binary.parentFile?.toPath() ?: continue
            // prependEntryToPATH handles ordering, the OS/remote path separator, and
            // local->remote path translation. Do NOT touch the read-only envs map directly.
            shellExecOptions.prependEntryToPATH(toolDir)
        }
    }
}
