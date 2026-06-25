package net.internetisalie.lunar.rocks.build

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.run.LuaRocksRunConfiguration
import net.internetisalie.lunar.rocks.run.LuaRocksRunConfigurationType
import net.internetisalie.lunar.rocks.run.LuaRocksSettings

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
        val exe = LuaRocksSettings.getInstance().executablePath
        for ((i, rock) in order.withIndex()) {
            ProgressManager.checkCanceled()
            indicator.text = "Building ${rock.packageName}"
            console.print("\n==> Building ${rock.packageName} (${i + 1}/${order.size}) …\n", ConsoleViewContentType.SYSTEM_OUTPUT)

            val exitCode = executeMake(project, rock, exe, console)
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
        console: ConsoleView
    ): Int {
        val configType = ConfigurationTypeUtil.findConfigurationType(LuaRocksRunConfigurationType::class.java)
        val factory = configType.configurationFactories.firstOrNull()
        val config = LuaRocksRunConfiguration(project, factory, "Workspace build: ${rock.packageName}").apply {
            command = "make"
            rockspecPath = rock.rockspec.toString()
        }

        val cmd = config.buildCommandLine(exe)
        val handler = try {
            ProcessHandlerFactory.getInstance().createColoredProcessHandler(cmd)
        } catch (e: ExecutionException) {
            console.print("Execution failed: ${e.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
            return -1
        }

        console.attachToProcess(handler)
        handler.startNotify()
        handler.waitFor()

        return handler.exitCode ?: -1
    }
}
