package net.internetisalie.lunar.run.test

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import net.internetisalie.lunar.command.newLuaInterpreterCommandLine
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.tool.LuaToolEnvironment
import net.internetisalie.lunar.tool.LuaToolManager
import net.internetisalie.lunar.tool.LuaToolType

class LuaTestCommandLineState(
    private val config: LuaTestRunConfiguration,
    environment: ExecutionEnvironment
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val commandLine = buildCommandLine()
        val processHandler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(processHandler)
        return processHandler
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()
        val properties = LuaTestConsoleProperties(config, executor)
        val console = SMTestRunnerConnectionUtil.createAndAttachConsole(
            "LuaTest", processHandler, properties
        )
        val actions = createActions(console, processHandler, executor)
        return DefaultExecutionResult(console, processHandler, *actions)
    }

    fun buildCommandLine(): GeneralCommandLine {
        val targetProject = config.project
        val commandLine = if (config.testFramework == LuaTestFramework.BUSTED) {
            buildBustedCommandLine(targetProject)
        } else {
            buildLunityCommandLine(targetProject)
        }

        config.environmentVariables?.configureCommandLine(commandLine, true)
        configureLuaPath(commandLine, targetProject)
        LuaToolEnvironment.prependToolDirsToPath(commandLine, targetProject)

        return commandLine
    }

    private fun buildBustedCommandLine(targetProject: Project): GeneralCommandLine {
        val bustedTool = LuaToolManager.getInstance().getEffectiveTool(targetProject, LuaToolType.BUSTED)
        if (bustedTool == null || !bustedTool.isValid) {
            throw ExecutionException("Busted not found. Install via LuaRocks: `luarocks install busted`")
        }
        val commandLine = GeneralCommandLine(bustedTool.path)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        
        val workDir = if (!config.workingDirectory.isNullOrEmpty()) config.workingDirectory else targetProject.basePath
        if (!workDir.isNullOrEmpty()) {
            commandLine.withWorkDirectory(workDir)
        }

        commandLine.addParameter("--output=json")
        configureBustedTargets(commandLine)

        val extraArgs = ParametersListUtil.parse(config.extraTestArguments.orEmpty())
        commandLine.addParameters(extraArgs)
        return commandLine
    }

    private fun configureBustedTargets(commandLine: GeneralCommandLine) {
        val failedTests = config.failedTestNames.orEmpty()
        if (failedTests.isNotEmpty()) {
            val filterPattern = failedTests.split(',').map { Regex.escape(it) }.joinToString("|")
            commandLine.addParameter("--filter=$filterPattern")
            return
        }

        val target = config.testTarget.orEmpty()
        if (target.isEmpty()) return

        when (config.testTargetType) {
            "FILE" -> commandLine.addParameter(target)
            "DIRECTORY" -> {
                commandLine.addParameter("--recursive")
                commandLine.addParameter(target)
            }
            "PATTERN" -> commandLine.addParameter("--filter=$target")
        }
    }

    private fun buildLunityCommandLine(targetProject: Project): GeneralCommandLine {
        val interpreter = config.interpreter ?: throw ExecutionException("Interpreter is not defined")
        val commandLine = newLuaInterpreterCommandLine(interpreter) ?: throw ExecutionException("Interpreter is not found")

        val workDir = if (!config.workingDirectory.isNullOrEmpty()) config.workingDirectory else targetProject.basePath
        if (!workDir.isNullOrEmpty()) {
            commandLine.withWorkDirectory(workDir)
        }

        val interpreterArgs = ParametersListUtil.parse(config.interpreterArguments.orEmpty())
        commandLine.addParameters(interpreterArgs)

        val target = config.testTarget.orEmpty()
        if (target.isNotEmpty()) {
            commandLine.addParameter(target)
        }

        val extraArgs = ParametersListUtil.parse(config.extraTestArguments.orEmpty())
        commandLine.addParameters(extraArgs)
        return commandLine
    }

    private fun configureLuaPath(commandLine: GeneralCommandLine, targetProject: Project) {
        val sourcePath = config.sourcePath.orEmpty()
        if (sourcePath.isNotEmpty()) {
            commandLine.withEnvironment("LUA_PATH", sourcePath)
        } else {
            val settingsState = LuaProjectSettings.getInstance(targetProject).state
            val luaPath = settingsState.expandSourcePath(targetProject)
            if (luaPath.isNotEmpty()) {
                commandLine.withEnvironment("LUA_PATH", luaPath)
            }
        }
    }
}
