package net.internetisalie.lunar.analysis.luacheck

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import kotlin.collections.addAll

private val DEFAULT_ARGS = arrayOf("--codes", "--ranges")

fun newLuaCheckCommandLine(
    targetFileName: String,
    workDirectory: VirtualFile,
) : GeneralCommandLine? {
    val settings = LuaCheckSettings.getInstance()
    val luaCheck = settings.state.executablePath
    if (luaCheck == null || luaCheck.isEmpty()) {
        return null
    }

    val cmd = GeneralCommandLine(settings.state.executablePath)
        .withWorkDirectory(workDirectory.path)

    val args: MutableList<String> = mutableListOf()
    val luaCheckArgs = settings.state.arguments
    if (luaCheckArgs != null && luaCheckArgs.isNotEmpty()) {
        val array = ParametersListUtil.parseToArray(luaCheckArgs)
        args.addAll(array)
    }
    args.addAll(DEFAULT_ARGS)

    cmd.addParameters(args.distinct())
    cmd.addParameter(targetFileName)
    return cmd
}
