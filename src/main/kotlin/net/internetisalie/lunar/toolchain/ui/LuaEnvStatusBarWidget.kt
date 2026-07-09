package net.internetisalie.lunar.toolchain.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainEvent
import net.internetisalie.lunar.toolchain.registry.LuaToolchainListener
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Status-bar widget showing the active Lua environment name; a click pops the full env set plus an
 * "Add environment…" item (TOOLING-05-06, design §2.7). Reads/switches the TOOLING-02 project
 * environment state. All callbacks run on the EDT: the label is a cheap state read and activation is
 * a pure state write + event publish (no I/O), so no background marshaling is required. Refresh is
 * driven by [LuaToolchainListener.TOPIC].
 */
class LuaEnvStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null

    override fun ID(): String = WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String =
        LuaToolchainProjectSettings.getInstance(project).activeEnvironment()?.name ?: NO_ENV_TEXT

    override fun getTooltipText(): String = "Active Lua environment (click to switch)"

    override fun getAlignment(): Float = 0f

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { showPopup(it) }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        project.messageBus.connect(this).subscribe(
            LuaToolchainListener.TOPIC,
            object : LuaToolchainListener {
                override fun toolchainChanged(event: LuaToolchainEvent) {
                    statusBar.updateWidget(WIDGET_ID)
                }
            },
        )
    }

    override fun dispose() {
        statusBar = null
    }

    private fun showPopup(event: MouseEvent) {
        try {
            val settings = LuaToolchainProjectSettings.getInstance(project)
            val step = EnvPopupStep(project, settings.environments(), settings.activeEnvironment()?.id)
            JBPopupFactory.getInstance().createListPopup(step).show(
                com.intellij.ui.awt.RelativePoint(event),
            )
        } catch (throwable: Throwable) {
            LOG.warn("Failed to open Lua environment popup", throwable)
        }
    }

    /** List popup enumerating [envs] (active one checked) plus a trailing add-environment item. */
    private class EnvPopupStep(
        private val project: Project,
        private val envs: List<LuaEnvironmentState>,
        private val activeId: String?,
    ) : BaseListPopupStep<Any>("Lua Environment", buildItems(envs)) {

        override fun getTextFor(value: Any): String =
            (value as? LuaEnvironmentState)?.name ?: ADD_ENV_TEXT

        override fun getIconFor(value: Any): Icon? =
            if (isActive(value, activeId)) AllIcons.Actions.Checked else null

        override fun onChosen(selectedValue: Any, finalChoice: Boolean): PopupStep<*>? {
            if (selectedValue is LuaEnvironmentState) {
                LuaToolchainProjectSettings.getInstance(project).activateEnvironment(selectedValue.id)
            } else {
                invokeProvisionAction()
            }
            return FINAL_CHOICE
        }

        private fun invokeProvisionAction() {
            val action = ActionManager.getInstance().getAction(PROVISION_ACTION_ID) ?: return
            val dataContext: DataContext = SimpleDataContext.getProjectContext(project)
            val actionEvent = AnActionEvent.createFromDataContext(WIDGET_PLACE, Presentation(), dataContext)
            action.actionPerformed(actionEvent)
        }

        companion object {
            private fun buildItems(envs: List<LuaEnvironmentState>): List<Any> = popupItems(envs)
        }
    }

    companion object {
        const val WIDGET_ID = "Lunar.LuaEnvWidget"
        const val NO_ENV_TEXT = "No Lua env"
        const val ADD_ENV_TEXT = "Add environment…"

        /** Test seam: the popup model = all envs, then the add-environment sentinel (design §2.7). */
        internal fun popupItems(envs: List<LuaEnvironmentState>): List<Any> = envs + ADD_ENV_TEXT

        /** Test seam: whether [value] is the active env row (checked in the popup). */
        internal fun isActive(value: Any, activeId: String?): Boolean =
            value is LuaEnvironmentState && value.id == activeId

        private const val PROVISION_ACTION_ID = "Lunar.Toolchain.Provision"
        private const val WIDGET_PLACE = "LunarEnvStatusBarWidget"
        private val LOG = Logger.getInstance(LuaEnvStatusBarWidget::class.java)
    }
}
