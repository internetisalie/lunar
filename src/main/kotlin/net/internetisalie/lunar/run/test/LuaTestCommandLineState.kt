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
import net.internetisalie.lunar.toolchain.exec.LuaExecutionEnvironmentBuilder
import net.internetisalie.lunar.toolchain.exec.LuaInterpreterCommandLines
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver
import java.nio.file.Path

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
        LuaExecutionEnvironmentBuilder.getInstance(targetProject)
            .build(config.sourcePath)
            .applyTo(commandLine)

        return commandLine
    }

    private fun buildBustedCommandLine(targetProject: Project): GeneralCommandLine {
        val bustedTool = LuaToolResolver.getInstance().resolve(targetProject, "busted")
            ?: throw ExecutionException(
                "Busted is not configured. Register or bind it under " +
                    "Settings | Languages & Frameworks | Lua | Toolchain (or install it via LuaRocks).",
            )
        val commandLine = GeneralCommandLine(bustedTool.path)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        
        val workDir = if (!config.workingDirectory.isNullOrEmpty()) config.workingDirectory else targetProject.basePath
        if (!workDir.isNullOrEmpty()) {
            commandLine.withWorkDirectory(workDir)
        }

        commandLine.addParameter("--output=json")
        if (environment.executor.id == "Coverage") {
            commandLine.addParameter("--coverage")
        }
        configureBustedTargets(commandLine)

        val extraArgs = ParametersListUtil.parse(config.extraTestArguments.orEmpty())
        commandLine.addParameters(extraArgs)
        return commandLine
    }

    private fun configureBustedTargets(commandLine: GeneralCommandLine) {
        val failedTests = config.failedTestNames.orEmpty()
        if (failedTests.isNotEmpty()) {
            failedTests.split(',')
                .filter { it.isNotBlank() }
                .forEach { commandLine.addParameter("--filter=${LuaPatternEscaper.escape(it)}") }
        }

        val target = config.testTarget.orEmpty()
        if (target.isEmpty()) return

        when (config.testTargetType) {
            "FILE" -> commandLine.addParameter(target)
            "DIRECTORY" -> {
                commandLine.addParameter("--recursive")
                commandLine.addParameter(target)
            }
            "PATTERN" -> {
                if (failedTests.isEmpty()) {
                    commandLine.addParameter("--filter=$target")
                }
            }
        }
    }

    private fun buildLunityCommandLine(targetProject: Project): GeneralCommandLine {
        val interpreter = config.resolveInterpreter()
            ?: throw ExecutionException(
                "No Lua runtime is configured. Add one under " +
                    "Settings | Languages & Frameworks | Lua | Toolchain.",
            )
        val commandLine = LuaInterpreterCommandLines.forBinary(Path.of(interpreter.path))

        val workDir = if (!config.workingDirectory.isNullOrEmpty()) config.workingDirectory else targetProject.basePath
        if (!workDir.isNullOrEmpty()) {
            commandLine.withWorkDirectory(workDir)
        }

        val interpreterArgs = ParametersListUtil.parse(config.interpreterArguments.orEmpty())
        commandLine.addParameters(interpreterArgs)
        if (environment.executor.id == "Coverage") {
            commandLine.addParameter("-lluacov")
        }

        val target = config.testTarget.orEmpty()
        if (target.isNotEmpty()) {
            commandLine.addParameter(target)
        }

        val extraArgs = ParametersListUtil.parse(config.extraTestArguments.orEmpty())
        commandLine.addParameters(extraArgs)
        return commandLine
    }
}
