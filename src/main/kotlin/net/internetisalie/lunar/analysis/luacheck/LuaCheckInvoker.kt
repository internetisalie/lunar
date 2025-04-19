/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.internetisalie.lunar.analysis.luacheck

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiManagerEx
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.util.LuaFileUtil
import net.internetisalie.lunar.util.LuaProcessUtil
import net.internetisalie.lunar.util.newProjectBackgroundTask

object LuaCheckInvoker {
    private val LOG = logger<LuaCheckInvoker>()

    fun invoke(project: Project, file: VirtualFile) {
        val settingsState = LuaCheckSettings.getInstance().state
        if (!settingsState.valid) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, LuaCheckSettingsPanel::class.java)
            return
        }

        val list: MutableList<Pair<String, PsiFile>> = mutableListOf()

        val dir: VirtualFile = if (file.isDirectory) file else file.parent
        if (file.isDirectory) {
            val leadingPathLength = file.canonicalPath!!.length + 1
            list.addAll(
                LuaFileUtil.findPsiFiles(
                    project,
                    LuaFileUtil.findLuaFilesInDir(dir)
                ).map {
                    Pair(
                        it.virtualFile.canonicalPath!!.substring(leadingPathLength),
                        it
                    )
                }
            )
        } else {
            val psiFile = PsiManagerEx.getInstance(project).findFile(file)
            if (psiFile != null)
                list.add(Pair(psiFile.name, psiFile))
        }

        newProjectBackgroundTask(
            LuaBundle.message("luacheck.name"),
            project,
        ) { indicator ->
            invokeMany(project, list.toTypedArray(), dir, indicator)
        }.queue()
    }

    private fun invokeMany(
        project: Project,
        fileList: Array<Pair<String, PsiFile>>,
        dir: VirtualFile,
        indicator: ProgressIndicator
    ) {
        val panel = LuaCheckView.getInstance(project).panel
        val builder = panel.builder
        val problems = mutableListOf<Problem>()

        var processFailure = false
        var idx = 0
        for ((relativeFilePath, psiFile) in fileList) {
            if (indicator.isCanceled) {
                break
            }
            indicator.text = psiFile.name
            indicator.fraction = idx.toDouble() / fileList.size
            idx++

            val cmd = newLuaCheckCommandLine(relativeFilePath, dir)
            if (cmd == null) {
                processFailure = true
                break
            }

            val listener = newLuaCheckCommandListener(psiFile, problems)

            try {
                LuaProcessUtil.listen(cmd, listener)
            } catch (_: ProcessNotCreatedException) {
                processFailure = true
                break
            }
        }

        if (processFailure) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, LuaCheckSettingsPanel::class.java)
        }

        builder.problems.clear()
        builder.problems.addAll(problems)
        builder.updateAsync()
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
