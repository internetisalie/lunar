package net.internetisalie.lunar.rocks.build

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
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

        val event = updateOnEdt(BuildWorkspaceAction())

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

        val event = updateOnEdt(BuildWorkspaceAction())

        assertTrue(event.presentation.isVisible)
        assertTrue(event.presentation.isEnabled)
    }

    // Runs the action's update on the EDT under a read action, matching the reliable pattern in
    // LuaRockspecDiscoveryServiceTest: entering the EDT flushes pending VFS/index events from
    // myFixture.addFileToProject so the freshly-added rockspecs are visible to FilenameIndex before
    // the gate counts them. SCHEMA-02 enables the JSON-Schema engine on .rockspec files, letting
    // JsonSchemaService schedule reindexing off the freshly-added rockspecs; waitUntilIndexesAreReady
    // settles that scanning first so the gate's index-backed count is not racing it.
    private fun updateOnEdt(action: BuildWorkspaceAction): AnActionEvent {
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        val dataContext = SimpleDataContext.getProjectContext(project)
        val event = TestActionEvent.createTestEvent(action, dataContext)
        runInEdtAndWait {
            ApplicationManager.getApplication().runReadAction { action.update(event) }
        }
        return event
    }

    @Test
    fun testActionUpdateThreadIsBgt() {
        val action = BuildWorkspaceAction()
        assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread())
    }
}
