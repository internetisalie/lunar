package net.internetisalie.lunar.run.console

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.console.ConsoleHistoryController
import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.toolchain.exec.LuaInterpreterCommandLines

/**
 * Launches the project Lua interpreter as an interactive REPL inside a [LuaConsoleView]
 * (RUN-03-01/02). Output is unbuffered via `setvbuf('no')` (RUN-03-08) and the session is
 * interactive via `-i`; history persists through [ConsoleHistoryController] (RUN-03-05).
 */
class LuaConsoleRunner(project: Project) :
    AbstractConsoleRunnerWithHistory<LuaConsoleView>(project, "Lua Console", null) {

    private val commandLine: GeneralCommandLine = buildCommandLine(project)

    override fun createProcess(): Process = commandLine.createProcess()

    override fun createProcessHandler(process: Process): OSProcessHandler =
        OSProcessHandler(process, commandLine.commandLineString, commandLine.charset)

    override fun createConsoleView(): LuaConsoleView = LuaConsoleView(project)

    override fun createExecuteActionHandler(): ProcessBackedConsoleExecuteActionHandler {
        val handler = LuaConsoleExecuteHandler(project, processHandler)
        ConsoleHistoryController(LuaConsoleRootType.instance, "LuaConsole", consoleView).install()
        return handler
    }

    private companion object {
        fun buildCommandLine(project: Project): GeneralCommandLine {
            val base = LuaInterpreterCommandLines.forProject(project)
                ?: throw ExecutionException(
                    "No Lua runtime is configured. Add one under " +
                        "Settings | Languages & Frameworks | Lua | Toolchain.",
                )
            return base.withParameters(
                "-e",
                "io.stdout:setvbuf('no'); io.stderr:setvbuf('no')",
                "-i",
            )
        }
    }
}
