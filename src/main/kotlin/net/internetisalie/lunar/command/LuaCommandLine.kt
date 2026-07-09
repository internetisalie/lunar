package net.internetisalie.lunar.command

import com.intellij.execution.configurations.GeneralCommandLine
import net.internetisalie.lunar.platform.LuaInterpreter
import java.nio.file.Path

/**
 * Builds a raw interpreter [GeneralCommandLine] from a legacy [LuaInterpreter] (jar → `java -cp`).
 *
 * TOOLING-05 Phase 4: the dead `newLuaDefaultInterpreterCommandLine`/`newProjectLuaInterpreterCommandLine`
 * factories were removed (replaced by `toolchain.exec.LuaInterpreterCommandLines`). Only the
 * single live caller `platform.LuaInterpreterService.identify` remains; this whole file is deleted in
 * Phase 5 with `LuaInterpreterService` (design §6.1#14, §9.1-b).
 */
fun newLuaInterpreterCommandLine(interpreter: LuaInterpreter): GeneralCommandLine? {
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
