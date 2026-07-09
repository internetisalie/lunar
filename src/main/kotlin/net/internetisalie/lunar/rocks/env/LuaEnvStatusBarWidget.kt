package net.internetisalie.lunar.rocks.env

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
import net.internetisalie.lunar.settings.LuaSettingsChangedListener
import net.internetisalie.lunar.util.newProjectBackgroundTask
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Status-bar widget showing the active hererocks environment label; a click pops the full env set
 * plus an "Add environment…" item (ROCKS-15-03, design §2.4, §3.4). All callbacks run on the EDT
 * (label read only, no I/O); the popup delegates switching to [HererocksEnvSet.switch].
 */
class LuaEnvStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null

    override fun ID(): String = WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String =
        HererocksEnvSet.active(project)?.displayLabel() ?: NO_ENV_TEXT

    override fun getTooltipText(): String = "Active Lua environment (click to switch)"

    override fun getAlignment(): Float = 0f

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { showPopup(it) }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        project.messageBus.connect(this).subscribe(
            LuaSettingsChangedListener.TOPIC,
            object : LuaSettingsChangedListener {
                override fun onSettingsChanged() {
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
            val envs = HererocksEnvSet.all(project)
            val activeId = HererocksEnvSet.active(project)?.id
            val step = EnvPopupStep(project, envs, activeId)
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
        private val envs: List<HererocksEnvState>,
        private val activeId: String?,
    ) : BaseListPopupStep<Any>("Lua Environment", buildItems(envs)) {

        override fun getTextFor(value: Any): String =
            (value as? HererocksEnvState)?.displayLabel() ?: ADD_ENV_TEXT

        override fun getIconFor(value: Any): Icon? =
            if (isActive(value, activeId)) AllIcons.Actions.Checked else null

        override fun onChosen(selectedValue: Any, finalChoice: Boolean): PopupStep<*>? {
            if (selectedValue is HererocksEnvState) {
                // Switch off the EDT: setActiveEnvAndNotify → bind() runs `luarocks --version` / `lua -v`
                // probes, which must not run on the EDT. bind() marshals its UI mutation back internally.
                newProjectBackgroundTask("Switching Lua environment", project) {
                    HererocksEnvSet.switch(project, selectedValue.id)
                }.queue()
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
            private fun buildItems(envs: List<HererocksEnvState>): List<Any> = popupItems(envs)
        }
    }

    companion object {
        const val WIDGET_ID = "Lunar.LuaEnvWidget"
        const val NO_ENV_TEXT = "No Lua env"
        const val ADD_ENV_TEXT = "Add environment…"

        /** Test seam: the popup model = all envs, then the add-environment sentinel (design §3.4). */
        internal fun popupItems(envs: List<HererocksEnvState>): List<Any> = envs + ADD_ENV_TEXT

        /** Test seam: whether [value] is the active env row (checked in the popup). */
        internal fun isActive(value: Any, activeId: String?): Boolean =
            value is HererocksEnvState && value.id == activeId

        // Transitional: the legacy Lunar.Hererocks.Create action was de-registered in TOOLING-04
        // P7, so "Add environment…" points at the new provisioning dialog. This whole widget is
        // rewired onto the TOOLING-02 environment model in TOOLING-05 (rocks/env → toolchain.ui).
        private const val PROVISION_ACTION_ID = "Lunar.Toolchain.Provision"
        private const val WIDGET_PLACE = "LunarEnvStatusBarWidget"
        private val LOG = Logger.getInstance(LuaEnvStatusBarWidget::class.java)
    }
}
