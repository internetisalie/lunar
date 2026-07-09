package net.internetisalie.lunar.analysis.luacheck

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver

private val DEFAULT_ARGS = arrayOf("--codes", "--ranges")

fun newLuaCheckCommandLine(
    project: Project,
    targetFileName: String,
    workDirectory: VirtualFile,
): GeneralCommandLine? {
    val tool = LuaToolResolver.getInstance().resolve(project, "luacheck") ?: return null
    val cmd = GeneralCommandLine(tool.path)
        .withWorkDirectory(workDirectory.path)

    cmd.addParameters(resolveArguments(project).distinct())
    cmd.addParameter(targetFileName)
    return cmd
}

private fun resolveArguments(project: Project): List<String> {
    val args = mutableListOf<String>()

    val configured = LuaToolchainProjectSettings.getInstance(project)
        .effectiveKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS)
    if (configured.isNotEmpty()) {
        args.addAll(ParametersListUtil.parseToArray(configured))
    }

    // Add target-specific --std if available (TARGET-05)
    LuaProjectSettings.getInstance(project).state.getTarget().getLuacheckStd()?.let { std ->
        args.add("--std")
        args.add(std)
    }

    args.addAll(DEFAULT_ARGS)
    return args
}
