package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Asks the platform to re-evaluate [LuaEnvStatusBarWidgetFactory.isAvailable] for a project. The
 * platform evaluates it once, when the frame's status bar is built, and again only on an explicit
 * `StatusBarWidgetsManager.updateWidget` (see `StatusBarWidgetFactory.isAvailable`), so a project
 * that gains its first environment after opening would otherwise never show the widget (BUG-534).
 */
interface LuaEnvWidgetAvailability {
    fun recheck()

    companion object {
        fun getInstance(project: Project): LuaEnvWidgetAvailability = project.service()
    }
}

/**
 * Posts the re-check to the EDT on the project's service scope, which ends with the project. The
 * disposed check runs on the EDT, where project disposal also runs, so it cannot race it.
 */
class LuaEnvWidgetAvailabilityImpl(
    private val project: Project,
    private val scope: CoroutineScope,
) : LuaEnvWidgetAvailability {
    override fun recheck() {
        scope.launch(Dispatchers.EDT) {
            if (project.isDisposed) return@launch
            project
                .service<StatusBarWidgetsManager>()
                .updateWidget(LuaEnvStatusBarWidgetFactory::class.java)
        }
    }
}
