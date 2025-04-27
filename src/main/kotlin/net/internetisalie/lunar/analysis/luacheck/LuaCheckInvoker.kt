package net.internetisalie.lunar.analysis.luacheck

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.util.LuaProcessUtil

object LuaCheckInvoker {
    private val LOG = logger<LuaCheckInvoker>()

    fun invoke(virtualFile : VirtualFile, psiFile : PsiFile) : List<Problem> {
        val dir = virtualFile.parent
        val relativeFilePath = virtualFile.name

        val cmd = newLuaCheckCommandLine(relativeFilePath, dir) ?: return emptyList()
        val problems = mutableListOf<Problem>()
        val listener = newLuaCheckCommandListener(psiFile, problems)

        try {
            LuaProcessUtil.listen(cmd, listener)
        } catch (_: ProcessNotCreatedException) { }

        return problems.toList()
    }

    private fun newLuaCheckCommandListener(
        psiFile: PsiFile,
        problems: MutableList<Problem>
    ): ProcessListener {
        return object : ProcessListener {
            val reg = "(.+?):(\\d+):(\\d+)-(\\d+):(.+)\\n".toRegex()

            override fun onTextAvailable(event: ProcessEvent, key: Key<*>) {
                val matchResult = reg.find(event.text)
                if (matchResult != null) {
                    //val matchGroup = matchResult.groups[1]!!
                    val lineGroup = matchResult.groups[2]!!
                    val colSGroup = matchResult.groups[3]!!
                    val colEGroup = matchResult.groups[4]!!
                    val descGroup = matchResult.groups[5]!!
                    val desc = descGroup.value.replace(Regex("\u001B\\[[;\\d]*m"), "");

                    LOG.debug("line=${lineGroup.value} col=${colSGroup.value}:${colEGroup.value} msg=${desc}")
                    problems.add(
                        Problem(
                            lineStart = lineGroup.value.toInt() - 1,
                            lineEnd = lineGroup.value.toInt() - 1,
                            columnStart = colSGroup.value.toInt() - 1,
                            columnEnd = colEGroup.value.toInt() - 1,
                            message = desc,
                            file = psiFile.name,
                            absFile = psiFile.virtualFile.canonicalPath,
                            psiFile = psiFile,
                        )
                    )
                }
            }
        }
    }
}
