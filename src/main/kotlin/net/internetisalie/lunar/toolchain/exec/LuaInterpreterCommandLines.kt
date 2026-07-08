package net.internetisalie.lunar.toolchain.exec

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver
import java.nio.file.Path

/**
 * Constructs interpreter [GeneralCommandLine]s (TOOLING-03-13); replaces
 * `command/LuaCommandLine.kt` (`newLuaInterpreterCommandLine` / `newProjectLuaInterpreterCommandLine`).
 * The `.jar → java -cp <jar> lua` special case is kept; the project interpreter comes from the
 * TOOLING-02 resolver's RUNTIME resolution.
 *
 * **Threading:** [forProject] resolves tools and builds the environment — callers are
 * `startProcess`/console builders, already off the EDT.
 */
object LuaInterpreterCommandLines {

    /** Replaces `newLuaInterpreterCommandLine` (command/LuaCommandLine.kt:32-45). Never returns null. */
    fun forBinary(executable: Path): GeneralCommandLine {
        val cmd = GeneralCommandLine(executable.toString())
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withWorkingDirectory(executable.parent)

        if (executable.fileName.toString().endsWith(".jar", ignoreCase = true)) {
            cmd.exePath = "java"
            cmd.addParameters("-cp", executable.toString(), "lua")
        }

        return cmd
    }

    /**
     * Replaces `newProjectLuaInterpreterCommandLine` (command/LuaCommandLine.kt:18-30):
     * resolver(RUNTIME) + full environment applied. Null when no runtime resolves.
     */
    fun forProject(project: Project): GeneralCommandLine? {
        val tool = LuaToolResolver.getInstance().resolveRuntime(project) ?: return null
        val cmd = forBinary(Path.of(tool.path))
        LuaExecutionEnvironmentBuilder.getInstance(project).build().applyTo(cmd)
        return cmd
    }
}
