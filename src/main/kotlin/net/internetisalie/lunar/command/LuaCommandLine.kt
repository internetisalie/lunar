package net.internetisalie.lunar.command

import com.intellij.execution.configurations.GeneralCommandLine
import net.internetisalie.lunar.settings.LuaApplicationSettings
import net.internetisalie.lunar.settings.LuaInterpreter
import java.io.File

fun findLuaInterpreter(interpreterPath : String) :  LuaInterpreter? {
    val settings = LuaApplicationSettings.instance.state
    val interpreters = settings.interpreters
    return interpreters.firstOrNull { interpreterPath == it.path }
}

fun newLuaDefaultInterpreterCommandLine(): GeneralCommandLine {
    val settings = LuaApplicationSettings.instance.state
    val interpreters = settings.interpreters
    val defaultInterpreter = interpreters.find { _ -> true } ?: error { "No lua interpreter found"}
    return newLuaInterpreterCommandLine(defaultInterpreter)
}

fun newLuaInterpreterCommandLine(interpreter : LuaInterpreter) : GeneralCommandLine {
    val defaultInterpreterPath = interpreter.path ?: error { "No path defined for lua interpreter" }
    val workDirectory = File(defaultInterpreterPath).parentFile

    return GeneralCommandLine(defaultInterpreterPath)
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withWorkDirectory(workDirectory)
}
