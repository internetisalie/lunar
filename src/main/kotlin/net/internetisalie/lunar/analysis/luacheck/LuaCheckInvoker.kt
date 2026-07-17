package net.internetisalie.lunar.analysis.luacheck

import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService

object LuaCheckInvoker {
    private val LOG = logger<LuaCheckInvoker>()

    private val LINE_PATTERN = "(.+?):(\\d+):(\\d+)-(\\d+):(.+)".toRegex()
    private val ANSI_PATTERN = Regex("\\[[;\\d]*m")

    fun invoke(virtualFile: VirtualFile, psiFile: PsiFile): List<Problem> {
        val dir = virtualFile.parent ?: return emptyList()
        val relativeFilePath = virtualFile.name

        val cmd = newLuaCheckCommandLine(psiFile.project, relativeFilePath, dir) ?: return emptyList()

        val output = try {
            LuaToolExecutionService.getInstance().capture(cmd, LuaExecTimeout.FORMAT)
        } catch (_: ExecutionException) {
            return emptyList()
        }

        return output.stdout.lineSequence()
            .mapNotNull { line -> problemFrom(line, psiFile) }
            .toList()
    }

    private fun problemFrom(line: String, psiFile: PsiFile): Problem? {
        val match = LINE_PATTERN.find(line) ?: return null
        val lineGroup = match.groups[2] ?: return null
        val colStartGroup = match.groups[3] ?: return null
        val colEndGroup = match.groups[4] ?: return null
        val descGroup = match.groups[5] ?: return null
        val message = descGroup.value.replace(ANSI_PATTERN, "")

        LOG.debug("line=${lineGroup.value} col=${colStartGroup.value}:${colEndGroup.value} msg=$message")
        return Problem(
            lineStart = lineGroup.value.toInt() - 1,
            lineEnd = lineGroup.value.toInt() - 1,
            columnStart = colStartGroup.value.toInt() - 1,
            columnEnd = colEndGroup.value.toInt() - 1,
            message = message,
            file = psiFile.name,
        )
    }
}
