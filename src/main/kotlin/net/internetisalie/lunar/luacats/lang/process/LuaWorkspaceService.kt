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
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.*
import net.internetisalie.lunar.command.LuaRunProfile
import net.internetisalie.lunar.command.newLuaDefaultInterpreterCommandLine
import net.internetisalie.lunar.util.LuaFileUtil
import net.internetisalie.lunar.util.LuaProcessUtil
import java.io.PipedReader
import java.io.PipedWriter

/**
 * Processing of `.luawork` files.
 */
@Service(Service.Level.PROJECT)
class LuaWorkspaceService(
    private val project: Project,
    private val cs: CoroutineScope,
) {
    companion object {
        /**
         * Return the singleton instance of the LuaWorkspaceService.
         */
        fun getInstance(): LuaWorkspaceService {
            return ProjectManager.getInstance().defaultProject.getService(LuaWorkspaceService::class.java)
        }

        private val LOGGER = logger<LuaWorkspaceService>()
        private val GSON = Gson()
        private val LUAWORK_FILE = arrayOf("lua", "luawork.lua")
    }

    /**
     * Asynchronously execute the workspace manifest executor and parse its
     * output into a new LuaWorkspace instance.
     */
    fun loadAsync(file: VirtualFile): Deferred<LuaWorkspace> {
        return cs.async { load(file) }
    }

    /**
     * Execute the workspace manifest executor and parse its output into a
     * new LuaWorkspace instance.
     */
    suspend fun load(file: VirtualFile): LuaWorkspace {
        // Create pipe ends
        val writer = PipedWriter()
        val reader = withContext(Dispatchers.IO) { PipedReader(writer) }

        // Pipe the process into the writer
        cs.launch {
            writer.use {
                process(file, it)
            }
        }

        // Decode the JSON output from the reader
        val result = cs.async {
            reader.use {
                GSON.fromJson(it, LuaWorkspace::class.java)
            }
        }.await()

        // Log the tree path entries
        result.trees.forEachIndexed { index, it -> LOGGER.warn("Workspace Tree #$index: $it") }

        return result
    }

    /**
     * Execute the workspace manifest executor and write its output to the supplied writer
     */
    fun process(file: VirtualFile, writer: PipedWriter) {
        var line = 0
        LuaProcessUtil.listen(newCommandLine(file),
            object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, key: Key<*>) {
                    if (line == 1) {
                        writer.write(event.text)
                    }
                    line++
                }
            })
    }

    /**
     * Execute the workspace manifest executor in a Run tool window tab
     */
    fun run(file: VirtualFile) {
        val commandLine = newCommandLine(file)
        val profile = LuaRunProfile(PtyCommandLine(commandLine))
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environmentBuilder = ExecutionEnvironmentBuilder(project, executor).runProfile(profile)
        val runner = ProgramRunner.getRunner(executor.id, profile) ?: return

        environmentBuilder.runner(runner).buildAndExecute()
    }

    private fun newCommandLine(file: VirtualFile): GeneralCommandLine {
        val luaWorkFile = LuaFileUtil.getPluginVirtualDirectoryChild(*LUAWORK_FILE) ?: error { "Could not locate luawork manifest loader" }
        val cmd = newLuaDefaultInterpreterCommandLine() ?: error("Could not create lua interpreter commandline")
        return cmd.withParameters(luaWorkFile.path, file.path)
    }

}
