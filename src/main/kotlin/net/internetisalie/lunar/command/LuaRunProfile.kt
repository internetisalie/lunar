package net.internetisalie.lunar.command

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.LuaIcons
import javax.swing.Icon

class LuaRunProfile (private val myCommandLine : GeneralCommandLine) : RunProfile {
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return LuaRunProfileState(environment)
    }

    override fun getName(): String {
        return LuaBundle.message("lua.run.profile.name")
    }

    override fun getIcon(): Icon {
        return LuaIcons.FILE
    }

    val commandLine : GeneralCommandLine
        get() = myCommandLine
}

class LuaRunProfileState(environment : ExecutionEnvironment) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler {
        val runProfile = environment.runProfile as LuaRunProfile? ?: error { "Expected LuaRunProfile" }
        val commandLine: GeneralCommandLine = runProfile.commandLine
        val processHandler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(processHandler)
        return processHandler
    }
}

