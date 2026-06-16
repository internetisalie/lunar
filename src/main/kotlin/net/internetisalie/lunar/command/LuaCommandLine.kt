package net.internetisalie.lunar.command

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.settings.LuaApplicationSettings
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.tool.LuaToolEnvironment
import java.nio.file.Path

fun newLuaDefaultInterpreterCommandLine(): GeneralCommandLine? {
    val settings = LuaApplicationSettings.instance.state
    val interpreters = settings.interpreters
    val defaultInterpreter = interpreters.find { _ -> true } ?: return null
    return newLuaInterpreterCommandLine(defaultInterpreter)
}

fun newProjectLuaInterpreterCommandLine(project: Project): GeneralCommandLine? {
    val settingsState = LuaProjectSettings.getInstance(project).state
    val interpreter = settingsState.interpreter ?: return null
    val cmd = newLuaInterpreterCommandLine(interpreter) ?: return null
    val luaPath = settingsState.expandSourcePath(project)
    if (luaPath.isNotEmpty()) {
        cmd.withEnvironment("LUA_PATH", luaPath)
    }
    // TOOL-02-03: make project-bound Lua tools (luarocks/luacheck/stylua) visible to the
    // interpreter subprocess by prepending their directories to PATH.
    LuaToolEnvironment.prependToolDirsToPath(cmd, project)
    return cmd
}

fun newLuaInterpreterCommandLine(interpreter : LuaInterpreter) : GeneralCommandLine? {
    val interpreterFile = interpreter.executable ?: return null

    val cmd = GeneralCommandLine(interpreterFile.path)
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withWorkingDirectory(Path.of(interpreterFile.parent.path))

    if ("jar" == interpreterFile.extension) {
        cmd.exePath = "java"
        cmd.addParameters("-cp", interpreterFile.path, "lua")
    }

    return cmd
}
