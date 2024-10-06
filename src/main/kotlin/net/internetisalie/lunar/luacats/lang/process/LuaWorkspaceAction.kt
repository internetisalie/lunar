package net.internetisalie.lunar.luacats.lang.process

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class LuaWorkspaceAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        LuaWorkspaceService.getInstance().loadAsync(file)
    }
}
