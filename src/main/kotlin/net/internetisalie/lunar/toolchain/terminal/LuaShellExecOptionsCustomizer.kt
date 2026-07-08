package net.internetisalie.lunar.toolchain.terminal

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.toolchain.exec.LuaExecutionEnvironmentBuilder
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import java.nio.file.Path

/**
 * TOOLING-03-12: terminal PATH injection. Moved from `tool/terminal/` (deleted in TOOLING-05); the
 * only behavioral change is that the prepend directories now come from
 * [LuaExecutionEnvironmentBuilder.pathPrependDirs], so the terminal PATH reflects the TOOLING-02
 * resolver and invalidates on `LuaToolchainListener.TOPIC` (fixing the stale-cache defect).
 *
 * Source-verified API (intellij-community 2026.1):
 * - Interface `org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer`
 *   (`@ApiStatus.Experimental`); EP id `org.jetbrains.plugins.terminal.shellExecOptionsCustomizer`.
 * - [customizeExecOptions] runs on a **background thread without a read action**
 *   (`@RequiresBackgroundThread` / `@RequiresReadLockAbsence`) — satisfied because
 *   [LuaExecutionEnvironmentBuilder.pathPrependDirs] touches no PSI/VFS.
 *
 * [MutableShellExecOptions.envs] is a **read-only** map: PATH is mutated only through
 * [MutableShellExecOptions.prependEntryToPATH], which inserts the entry first, joins with the
 * *remote* path separator, and translates local→remote paths. Because it inserts at the front, the
 * dirs (highest priority first) are iterated in reverse to keep the highest-priority dir first.
 */
class LuaShellExecOptionsCustomizer : ShellExecOptionsCustomizer {
    override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
        val prependDirs = LuaExecutionEnvironmentBuilder.getInstance(project).pathPrependDirs()
        prependInReverse(prependDirs, shellExecOptions::prependEntryToPATH)
    }

    companion object {
        /**
         * Feeds [prependDirs] (highest priority first) to [prepend] in reverse so that a
         * front-inserting sink keeps the highest-priority dir first on the resulting PATH.
         */
        @TestOnly
        internal fun prependInReverse(prependDirs: List<Path>, prepend: (Path) -> Unit) {
            for (dir in prependDirs.asReversed()) {
                prepend(dir)
            }
        }
    }
}
