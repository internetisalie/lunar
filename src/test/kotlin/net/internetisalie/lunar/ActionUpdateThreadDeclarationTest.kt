package net.internetisalie.lunar

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.coverage.LuaCovReportImportAction
import net.internetisalie.lunar.rocks.matrix.RunMatrixAction
import net.internetisalie.lunar.rocks.publish.PublishRockAction
import net.internetisalie.lunar.run.console.LuaConsoleAction

/**
 * BUG-528: actions that override `update` must declare their update thread. An undeclared one
 * defaults to EDT, where rule-provided data such as `VIRTUAL_FILE` raises an IDE error.
 */
class ActionUpdateThreadDeclarationTest : BasePlatformTestCase() {
    fun testPublishRockActionUpdatesOnBackgroundThread() {
        assertEquals(ActionUpdateThread.BGT, PublishRockAction().actionUpdateThread)
    }

    fun testRunMatrixActionUpdatesOnBackgroundThread() {
        assertEquals(ActionUpdateThread.BGT, RunMatrixAction().actionUpdateThread)
    }

    fun testLuaCovReportImportActionUpdatesOnBackgroundThread() {
        assertEquals(ActionUpdateThread.BGT, LuaCovReportImportAction().actionUpdateThread)
    }

    fun testLuaConsoleActionUpdatesOnBackgroundThread() {
        assertEquals(ActionUpdateThread.BGT, LuaConsoleAction().actionUpdateThread)
    }

    fun testPublishRockActionShownForRockspec() {
        val rockspec = myFixture.configureByText("app-scm-1.rockspec", "package = \"app\"\n").virtualFile
        assertTrue(updatedWith(PublishRockAction(), rockspec).presentation.isEnabledAndVisible)
    }

    fun testPublishRockActionHiddenForLuaFile() {
        val luaFile = myFixture.configureByText("init.lua", "return {}\n").virtualFile
        assertFalse(updatedWith(PublishRockAction(), luaFile).presentation.isEnabledAndVisible)
    }

    private fun updatedWith(
        action: AnAction,
        selected: VirtualFile,
    ): AnActionEvent {
        val dataContext =
            SimpleDataContext
                .builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.VIRTUAL_FILE, selected)
                .build()
        val event = TestActionEvent.createTestEvent(action, dataContext)
        ApplicationManager.getApplication().runReadAction { action.update(event) }
        return event
    }
}
