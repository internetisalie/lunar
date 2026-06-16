package net.internetisalie.lunar.tool

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Path

/**
 * Patches a [GeneralCommandLine]'s `PATH` so that the project's effective Lua tool binary
 * directories (TOOL-02) are visible to subprocesses the IDE spawns — Lua interpreters, REPL
 * consoles, luacheck, and ROCKS operations (TOOL-02-02/03).
 *
 * Why this and not a platform `EnvironmentProvider`/`RunConfigurationExtension`?
 *  - There is **no** platform `EnvironmentProvider` interface (grounding-audit fix #1).
 *  - `RunConfigurationExtension` lives in the Java `execution-impl` module, which is **not** on
 *    this plugin's compile classpath (Lunar depends only on `com.intellij.modules.platform`, not
 *    `com.intellij.modules.java`). Patching the command line directly at the point of construction
 *    in `command/LuaCommandLine.kt` keeps the integration self-contained and dependency-free, and
 *    covers exactly the processes Lunar itself launches.
 *
 * The tool directories are sourced from the single project-level
 * [LuaTerminalEnvironmentService] so the terminal and run-config paths agree.
 *
 * **PATH semantics:** tool dirs are prepended (highest priority first) ahead of the existing PATH,
 * joined with the OS-appropriate [File.pathSeparator]. Existing PATH entries are preserved.
 * Note: unlike the terminal customizer's `prependEntryToPATH`, no local→remote (WSL/Docker/SSH)
 * translation is applied — these command lines run in the local IDE environment.
 */
object LuaToolEnvironment {

    private val log = logger<LuaToolEnvironment>()

    /**
     * Prepends [project]'s effective Lua tool directories to [commandLine]'s `PATH` env var.
     * No-op when [project] is `null` or no tool directories resolve.
     *
     * @return [commandLine] (for fluent chaining).
     */
    fun prependToolDirsToPath(commandLine: GeneralCommandLine, project: Project?): GeneralCommandLine {
        if (project == null) return commandLine
        val toolDirs = LuaTerminalEnvironmentService.getInstance(project).getToolDirectories()
        return prependToolDirsToPath(commandLine, toolDirs)
    }

    /**
     * Pure-function form used by the resolution above and by tests: prepends [toolDirs] to the
     * command line's `PATH`. Visible for testing so the join/precedence logic can be verified
     * without a live project service.
     */
    fun prependToolDirsToPath(commandLine: GeneralCommandLine, toolDirs: List<Path>): GeneralCommandLine {
        if (toolDirs.isEmpty()) return commandLine

        val prefix = toolDirs.joinToString(File.pathSeparator) { it.toString() }

        // GeneralCommandLine.environment overrides the parent env for matching keys, so we must
        // fold in the existing PATH ourselves. Prefer an already-set override, then the inherited
        // process PATH, so we never silently drop entries.
        val existing = commandLine.environment["PATH"]
            ?: System.getenv("PATH")
            ?: ""

        val newPath = if (existing.isBlank()) prefix else "$prefix${File.pathSeparator}$existing"
        commandLine.environment["PATH"] = newPath
        log.debug("Prepended ${toolDirs.size} Lua tool dir(s) to PATH")
        return commandLine
    }
}
