package net.internetisalie.lunar.rocks.build

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import net.internetisalie.lunar.rocks.LuaRocksEnvironment
import net.internetisalie.lunar.rocks.run.LuaRocksRunConfiguration
import net.internetisalie.lunar.rocks.run.LuaRocksRunConfigurationType
import net.internetisalie.lunar.toolchain.exec.LuaExecOutcome
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService

object WorkspaceBuildRunner {

    data class BuildOutcome(
        val builtCount: Int,
        val failedRock: WorkspaceRock?,
        val exitCode: Int?
    )

    fun run(
        project: Project,
        order: List<WorkspaceRock>,
        console: ConsoleView,
        indicator: ProgressIndicator
    ): BuildOutcome {
        val exe = LuaRocksEnvironment.resolveExecutable(project)
        if (exe == null) {
            console.print(
                "\nLuaRocks is not configured. Register or bind it under " +
                    "Settings | Languages & Frameworks | Lua | Toolchain.\n",
                ConsoleViewContentType.ERROR_OUTPUT,
            )
            return BuildOutcome(builtCount = 0, failedRock = order.firstOrNull(), exitCode = -1)
        }
        for ((i, rock) in order.withIndex()) {
            ProgressManager.checkCanceled()
            indicator.text = "Building ${rock.packageName}"
            console.print("\n==> Building ${rock.packageName} (${i + 1}/${order.size}) …\n", ConsoleViewContentType.SYSTEM_OUTPUT)

            val exitCode = executeMake(project, rock, exe, console, indicator)
            if (exitCode != 0) {
                return BuildOutcome(builtCount = i, failedRock = rock, exitCode = exitCode)
            }
        }
        return BuildOutcome(builtCount = order.size, failedRock = null, exitCode = null)
    }

    private fun executeMake(
        project: Project,
        rock: WorkspaceRock,
        exe: String,
        console: ConsoleView,
        indicator: ProgressIndicator,
    ): Int {
        val configType = ConfigurationTypeUtil.findConfigurationType(LuaRocksRunConfigurationType::class.java)
        val factory = configType.configurationFactories.firstOrNull()
        val config = LuaRocksRunConfiguration(project, factory, "Workspace build: ${rock.packageName}").apply {
            command = "make"
            rockspecPath = rock.rockspec.toString()
        }

        val cmd = config.buildCommandLine(exe)
        val result = LuaToolExecutionService.getInstance()
            .stream(cmd, ConsolePrintingListener(console), LuaExecTimeout.INSTALL, colored = true, indicator = indicator)
        return if (result.outcome == LuaExecOutcome.COMPLETED) result.exitCode else -1
    }

    private class ConsolePrintingListener(private val console: ConsoleView) : ProcessListener {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            console.print(event.text, ConsoleViewContentType.getConsoleViewType(outputType))
        }
    }
}
