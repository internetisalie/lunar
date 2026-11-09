package net.internetisalie.lunar.toolchain.discovery

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable
import org.jetbrains.annotations.TestOnly

/**
 * Discovers Lua tools on `PATH` the first time a project opens with an empty toolchain registry
 * (BUG-63). Discovery otherwise ran only from the Toolchain settings' Discover button, so a fresh
 * install with `lua` and `luarocks` installed behaved as if neither existed. Runs at most once per
 * installation, and only while no tool is registered, so a user who later removes tools is not
 * overruled; the Discover button stays the way to rescan.
 */
class LuaFirstRunDiscovery : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        val found =
            withContext(Dispatchers.IO) { discoverIfEmpty(LuaToolchainRegistry.getInstance()) { it.autoDiscover() } }
        if (!found.isNullOrEmpty()) notifyFound(project, found)
    }

    companion object {
        private const val DONE_KEY = "lunar.toolchain.firstRunDiscoveryDone"

        /** Runs [discover] once per installation while [registry] holds no tool; returns what it registered, or null. */
        fun discoverIfEmpty(
            registry: LuaToolchainRegistry,
            discover: (LuaToolchainRegistry) -> Unit,
        ): List<LuaRegisteredTool>? {
            val properties = PropertiesComponent.getInstance()
            if (registry.tools().isNotEmpty() || properties.getBoolean(DONE_KEY)) return null
            properties.setValue(DONE_KEY, true)
            discover(registry)
            return registry.tools()
        }

        @TestOnly
        fun resetForTest() = PropertiesComponent.getInstance().unsetValue(DONE_KEY)

        private fun notifyFound(
            project: Project,
            found: List<LuaRegisteredTool>,
        ) {
            val names = found.joinToString(", ") { listOfNotNull(it.kindId, it.version).joinToString(" ") }
            NotificationGroupManager
                .getInstance()
                .getNotificationGroup("notification.group.lunar.tools")
                .createNotification("Lua tools found on PATH", "Lunar registered $names.", NotificationType.INFORMATION)
                .addAction(
                    NotificationAction.createSimpleExpiring("Review toolchain") {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, LuaToolchainConfigurable::class.java)
                    },
                ).notify(project)
        }
    }
}
