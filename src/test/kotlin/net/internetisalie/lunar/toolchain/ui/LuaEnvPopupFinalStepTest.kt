package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * BUG-537: a popup choice that opens a dialog must hand it to the popup's final runnable, which the
 * platform runs after the popup closes. Showing it from `onChosen` logs a SEVERE from `PopupImplUtil`.
 */
class LuaEnvPopupFinalStepTest : BasePlatformTestCase() {
    fun `test add environment defers the provision action until the popup closes BUG537`() {
        val invocations = mutableListOf<AnActionEvent>()
        withProvisionAction(RecordingAction(invocations)) {
            val step = LuaEnvStatusBarWidget.EnvPopupStep(project, emptyList(), null)

            val next = step.onChosen(LuaEnvStatusBarWidget.ADD_ENV_TEXT, true)

            assertSame(PopupStep.FINAL_CHOICE, next)
            assertEquals(0, invocations.size)
            val finalRunnable = step.finalRunnable ?: return@withProvisionAction fail("No final runnable")
            finalRunnable.run()
            assertEquals(1, invocations.size)
        }
    }

    fun `test project chooser defers provisioning until the popup closes BUG537`() {
        val provisioned = mutableListOf<Project>()
        val step = ProvisionTargetStep(listOf(project)) { provisioned += it }

        val next = step.onChosen(project, true)

        assertSame(PopupStep.FINAL_CHOICE, next)
        assertEquals(0, provisioned.size)
        val finalRunnable = step.finalRunnable ?: return fail("No final runnable")
        finalRunnable.run()
        assertEquals(listOf(project), provisioned)
    }

    private fun withProvisionAction(
        replacement: AnAction,
        body: () -> Unit,
    ) {
        val actionManager = ActionManager.getInstance()
        val registered = actionManager.getAction(PROVISION_ACTION_ID)
        val original = checkNotNull(registered) { "$PROVISION_ACTION_ID is not registered" }
        actionManager.replaceAction(PROVISION_ACTION_ID, replacement)
        try {
            body()
        } finally {
            actionManager.replaceAction(PROVISION_ACTION_ID, original)
        }
    }

    private class RecordingAction(
        private val invocations: MutableList<AnActionEvent>,
    ) : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
            invocations += e
        }
    }

    private companion object {
        const val PROVISION_ACTION_ID = "Lunar.Toolchain.Provision"
    }
}
