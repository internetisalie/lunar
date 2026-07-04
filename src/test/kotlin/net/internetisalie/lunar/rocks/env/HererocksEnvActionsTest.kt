package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import net.internetisalie.lunar.settings.LuaProjectSettings

/** Phase 6: Upgrade/Recreate/Remove are disabled without a bound env, enabled with one. */
@Suppress("DEPRECATION")
class HererocksEnvActionsTest : EnvSettingsTestCase() {

    private fun update(action: AnAction): AnActionEvent {
        val dataContext = SimpleDataContext.getProjectContext(project)
        return TestActionEvent.createTestEvent(action, dataContext).also { action.update(it) }
    }

    fun testDisabledWithoutBoundEnv() {
        LuaProjectSettings.getInstance(project).state.hererocksEnv = null
        for (action in listOf(UpgradeHererocksEnvAction(), RecreateHererocksEnvAction(), RemoveHererocksEnvAction())) {
            assertFalse(action.javaClass.simpleName, update(action).presentation.isEnabled)
        }
    }

    fun testEnabledWithBoundEnv() {
        LuaProjectSettings.getInstance(project).state.hererocksEnv =
            HererocksEnvState(directory = "/p/.lua", flavor = HererocksFlavor.PUC, luaVersion = "5.4")
        for (action in listOf(UpgradeHererocksEnvAction(), RecreateHererocksEnvAction(), RemoveHererocksEnvAction())) {
            assertTrue(action.javaClass.simpleName, update(action).presentation.isEnabled)
        }
    }
}
