package net.internetisalie.lunar.rocks.build

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test

/**
 * Action-gate test for [BuildWorkspaceAction.update] (TC #8 and TC #9).
 */
class BuildWorkspaceActionTest : IndexedBasePlatformTestCase() {

    @Test
    fun testActionDisabledForOneRock() {
        // TC #8: 1 rock => disabled
        myFixture.addFileToProject(
            "rocks/a/a-1.0-1.rockspec",
            "package = \"a\"\nversion = \"1.0-1\"\n"
        )

        val action = BuildWorkspaceAction()
        val dataContext = SimpleDataContext.getProjectContext(project)
        val event = TestActionEvent.createTestEvent(action, dataContext)

        action.update(event)

        assertTrue(event.presentation.isVisible)
        assertFalse(event.presentation.isEnabled)
    }

    @Test
    fun testActionEnabledForTwoOrMoreRocks() {
        // TC #9: >=2 rocks => enabled
        myFixture.addFileToProject(
            "rocks/a/a-1.0-1.rockspec",
            "package = \"a\"\nversion = \"1.0-1\"\n"
        )
        myFixture.addFileToProject(
            "rocks/b/b-1.0-1.rockspec",
            "package = \"b\"\nversion = \"1.0-1\"\n"
        )

        val action = BuildWorkspaceAction()
        val dataContext = SimpleDataContext.getProjectContext(project)
        val event = TestActionEvent.createTestEvent(action, dataContext)

        action.update(event)

        assertTrue(event.presentation.isVisible)
        assertTrue(event.presentation.isEnabled)
    }

    @Test
    fun testActionUpdateThreadIsBgt() {
        val action = BuildWorkspaceAction()
        assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread())
    }
}
