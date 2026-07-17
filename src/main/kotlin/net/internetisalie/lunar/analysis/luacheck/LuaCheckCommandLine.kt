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

    cmd.addParameters(dedupePairs(resolveArguments(project)))
    cmd.addParameter(targetFileName)
    return cmd
}

internal fun dedupePairs(tokens: List<String>): List<String> {
    val accumulator = DedupAccumulator()
    var index = 0
    while (index < tokens.size) {
        val current = tokens[index]
        val next = tokens.getOrNull(index + 1)
        index += if (isFlag(current) && next != null && !next.startsWith("-")) {
            accumulator.appendPair(current, next)
        } else {
            accumulator.appendLone(current)
        }
    }
    return accumulator.result
}

private fun isFlag(token: String): Boolean =
    token.startsWith("--") || (token.startsWith("-") && token.length > 1 && !token[1].isDigit())

private class DedupAccumulator {
    val result = mutableListOf<String>()
    private val seen = mutableSetOf<String>()

    fun appendPair(flag: String, value: String): Int {
        if (seen.add("$flag $value")) {
            result.add(flag)
            result.add(value)
        }
        return 2
    }

    fun appendLone(token: String): Int {
        if (seen.add(token)) result.add(token)
        return 1
    }
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
