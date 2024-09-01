/*
 * Copyright 2016 Jon S Akhtar (Sylvanaar)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package net.internetisalie.lunar.settings

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import net.internetisalie.lunar.settings.LuaInterpreterFamily.Companion.findByMatch
import net.internetisalie.lunar.util.LuaFileUtil
import net.internetisalie.lunar.util.LuaGlobUtil
import net.internetisalie.lunar.util.LuaSystemUtil
import java.util.regex.Pattern

/**
 * Searches platform-specific common paths to find
 * Lua interpreters.
 */
class LuaInterpreterFinder {
    private var searchPaths: Array<String> = if (SystemInfo.isWindows) PATHS_WINDOWS else PATHS_UNIX

    private fun getDirectory(path: String?): VirtualFile? {
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$path")
        return if ((virtualFile != null && virtualFile.exists()
                    && virtualFile.isDirectory)
        )
            virtualFile
        else
            null
    }

    private fun substituteEnvVars(into: String): String {
        var into = into
        var m = envVarPattern.matcher(into)
        while (m.matches()) {
            val varName = m.group(1)
            var varValue = System.getenv(varName)
            if (varValue == null) varValue = ""
            into = into.replace("\${$varName}", varValue)
            m = envVarPattern.matcher(into)
        }
        return into
    }

    private fun getJarProcessOutput(interpreterExecutable: VirtualFile, container: VirtualFile): ProcessOutput? {
        // Convert the virtual file of the container back to a path string
        val workingDirectory = container.canonicalPath ?: return null

        val exePath = interpreterExecutable.canonicalPath ?: return null

        // Execute the process and ask for its version number
        val processOutput: ProcessOutput
        try {
            processOutput = LuaSystemUtil.getProcessOutput(
                workingDirectory,
                "java",
                "-cp",
                exePath,
                "lua",
                "-v"
            )
        } catch (e2: ExecutionException) {
            return null
        }

        return processOutput
    }

    private fun getBinaryProcessOutput(interpreterExecutable: VirtualFile, container: VirtualFile): ProcessOutput? {
        // Convert the virtual file of the container back to a path string
        val workingDirectory = container.canonicalPath ?: return null

        val exePath = interpreterExecutable.canonicalPath ?: return null

        // Execute the process and ask for its version number
        var processOutput: ProcessOutput
        try {
            processOutput = LuaSystemUtil.getProcessOutput(
                workingDirectory,
                exePath,
                "--version"
            )
            if (processOutput.exitCode != 0) throw ExecutionException("Invalid parameter")
        } catch (e1: ExecutionException) {
            try {
                processOutput = LuaSystemUtil.getProcessOutput(
                    workingDirectory,
                    exePath,
                    "-v"
                )
            } catch (e2: ExecutionException) {
                return null
            }
        }

        return processOutput
    }

    private fun setAsInvalid(interpreter: LuaInterpreter) {
        interpreter.familyKey = LuaInterpreterFamily.INVALID_KEY
        interpreter.version = null
    }

    fun describe(interpreter: LuaInterpreter) {
        val exePath = interpreter.path

        if (exePath == null || exePath.isEmpty() || exePath.trim { it <= ' ' }.isEmpty()) {
            setAsInvalid(interpreter)
            return
        }

        // Locate the virtual file
        val vfsManager = VirtualFileManager.getInstance()
        val interpreterExeFile = vfsManager.findFileByUrl("file://" + exePath.trim { it <= ' ' })
        if (interpreterExeFile == null) {
            setAsInvalid(interpreter)
            return
        }

        // Launch the process
        val processOutput = if ("jar" == interpreterExeFile.extension) {
            getJarProcessOutput(
                interpreterExeFile,
                interpreterExeFile.parent
            )
        } else {
            getBinaryProcessOutput(
                interpreterExeFile,
                interpreterExeFile.parent
            )
        }

        if (processOutput == null) {
            setAsInvalid(interpreter)
            return
        }

        var outputText = processOutput.stderr.trim { it <= ' ' }
        if (outputText.isEmpty()) outputText = processOutput.stdout.trim { it <= ' ' }

        if (outputText.contains("\n")) {
            val lines = outputText.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            outputText = lines[0]
        }

        if (outputText.isEmpty()) {
            setAsInvalid(interpreter)
            return
        }

        // Find the name and version number
        val versionPattern = Pattern.compile("^(\\S+)\\s+(\\S+).*")
        val m = versionPattern.matcher(outputText)
        if (!m.matches()) {
            setAsInvalid(interpreter)
            return
        }

        // Find a matching family
        val familyMatch = m.group(1)
        val interpreterFamily = findByMatch(
            familyMatch,
            interpreterExeFile.name
        )
        if (interpreterFamily == null) {
            setAsInvalid(interpreter)
            return
        }

        // Success
        interpreter.familyKey = interpreterFamily.key()
        interpreter.version = m.group(2)
        interpreter.path = exePath.trim { it <= ' ' }
    }

    private fun validateInterpreter(executable: VirtualFile?, family: LuaInterpreterFamily): LuaInterpreter? {
        if (executable == null) return null

        val possibleResult = LuaInterpreter()
        possibleResult.path = executable.path
        describe(possibleResult)
        if (family.familyNameMatch == possibleResult.family!!.familyNameMatch) {
            possibleResult.familyKey = family.familyNameMatch
            possibleResult.name = possibleResult.family!!.interpreterName + " (System)"
            return possibleResult
        }

        return null
    }

    private fun interpretersInPath(directoryName: String?) : List<LuaInterpreter>{
        val directory = getDirectory(directoryName) ?: return emptyList()
        val results = ArrayList<LuaInterpreter>()

        for (family in LuaInterpreterFamily.FAMILIES.values) {
            val exeName = family.platformExecutableName

            if (LuaGlobUtil.isGlob(exeName)) {
                // Match the glob
                val globPattern = LuaGlobUtil.patternFromGlob(exeName)
                for (executable in directory.children) {
                    if (!globPattern.matcher(executable.name).matches()) continue

                    val result = validateInterpreter(executable, family)
                    if (result != null) results.add(result)
                }
            } else {
                logger<LuaInterpreterFinder>().warn("Checking lua binary ${directoryName}/${exeName}")
                val executable = directory.findChild(exeName)
                val result = validateInterpreter(executable, family)
                if (result != null) results.add(result)
            }
        }

        return results
    }

    fun findInterpreters(): List<LuaInterpreter> {
        val results = ArrayList<LuaInterpreter>()

        // Search each search path after env var substitution
        searchPaths.flatMapTo(results, {
            searchPath ->
            interpretersInPath(substituteEnvVars(searchPath))
        })

        // Search lib directory
        val libDirectory = LuaFileUtil.getPluginVirtualDirectoryChild("lib")
        logger<LuaInterpreterFinder>().warn("Plugin lib directory: ${libDirectory?.canonicalPath}")
        if (libDirectory != null) {
            results.addAll(interpretersInPath(libDirectory.canonicalPath))
        }

        return results
    }

    fun describeInBackground(luaInterpreter: LuaInterpreter, callback : ((LuaInterpreter) -> Unit)? = null) {
        object : Task.Backgroundable(
            ProjectManager.getInstance().defaultProject,
            "Describing lua interpreter",
            false
        ) {
            override fun run(indicator: ProgressIndicator) {
                describe(luaInterpreter)
                if (callback != null) {
                    callback(luaInterpreter)
                }
            }
        }.queue()
    }

    companion object {
        val PATHS_UNIX: Array<String> = arrayOf(
            "/bin",
//            "/sbin",
            "/usr/bin",
//            "/usr/sbin",
//            "/usr/local/bin",
//            "/usr/local/sbin",
//            "/opt/bin",
//            "/opt/sbin",
//            "/opt/local/bin",
//            "/opt/local/sbin",
//            "\${HOME}/bin",
//            "\${HOME}/sbin",
//            "\${HOME}/torch/install/bin",
        )

        // TODO: Search Path Globs
        val PATHS_WINDOWS: Array<String> = arrayOf(
            "C:\\Program Files\\Lua 5.1",
            "C:\\Program Files\\Lua 5.2",
            "C:\\Program Files\\Lua 5.3",
            "C:\\Program Files\\Lua 5.4",
            "C:\\Program Files (x86)\\Lua 5.1",
            "C:\\Program Files (x86)\\Lua 5.2",
            "C:\\Program Files (x86)\\Lua 5.3",
            "C:\\Program Files (x86)\\Lua 5.4",
        )

        val envVarPattern: Pattern = Pattern.compile(".*\\$\\{([^\\}]+)\\}.*")

        @JvmField
        val INSTANCE: LuaInterpreterFinder = LuaInterpreterFinder()
    }
}
