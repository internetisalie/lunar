// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package net.internetisalie.lunar.luacats.lang.process

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.internetisalie.lunar.command.LuaRunProfile
import net.internetisalie.lunar.command.newLuaDefaultInterpreterCommandLine
import java.io.PipedReader
import java.io.PipedWriter

class LuaWorkAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        LuaWorkService.getInstance().loadAsync(file)
    }
}

@Service(Service.Level.PROJECT)
class LuaWorkService(
    private val project: Project,
    private val cs: CoroutineScope
) {
    companion object {
        fun getInstance() : LuaWorkService {
            return ProjectManager.getInstance().defaultProject.getService(LuaWorkService::class.java)
        }

        val LOGGER = logger<LuaWorkAction>()
    }

    fun loadAsync(file : VirtualFile) {
        cs.launch {
            val workspace = load(file)
            workspace.trees.forEachIndexed { index, it ->
                LOGGER.warn("$index: $it")
            }
        }
    }

    fun loadRun(file : VirtualFile) {
        val commandLine = newCommandLine(file)
        val profile = LuaRunProfile(PtyCommandLine(commandLine))
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environmentBuilder = ExecutionEnvironmentBuilder(project, executor).runProfile(profile)
        val runner = ProgramRunner.getRunner(executor.id, profile) ?: return

        environmentBuilder.runner(runner).buildAndExecute()
    }

    suspend fun load(file: VirtualFile) : LuaWork {
        val commandLine = newCommandLine(file)
        val handler = OSProcessHandler(commandLine)

        val writer = PipedWriter()
        val reader = withContext(Dispatchers.IO) {
            PipedReader(writer)
        }
        var line = 0

        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, key: Key<*>) {
                if (line == 1) {
                    writer.write(event.text)
                }
                line++
            }
        })
        handler.startNotify()
        handler.waitFor()

        withContext(Dispatchers.IO) {
            writer.close()
        }

        val result = Gson().fromJson(reader, LuaWork::class.java)

        withContext(Dispatchers.IO) {
            reader.close()
        }

        return result
    }

    private fun newCommandLine(file : VirtualFile) : GeneralCommandLine {
        val commandLine = newLuaDefaultInterpreterCommandLine()
        commandLine.addParameter("/home/mini/Documents/src/lua/lunar/src/main/lua/luawork.lua")
        commandLine.addParameter(file.path)
        return commandLine
    }

}

data class LuaWork(val trees : List<String>) {
    val luaPath : String
        get() {
            val sb = StringBuilder()
            for (tree in trees) {
                sb
                    .append(tree).append("/?.lua;")
                    .append(tree).append("/?/init.lua;")
            }
            sb.append(";")  // append default package path
            return sb.toString()
        }

    val luaCPath : String
        get() {
            val sb = StringBuilder()
            for (tree in trees) {
                sb.append(tree).append("/?.so;")
            }
            sb.append(";")  // append default package path
            return sb.toString()
        }
}
