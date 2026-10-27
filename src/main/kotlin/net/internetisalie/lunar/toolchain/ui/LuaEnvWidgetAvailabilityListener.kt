package net.internetisalie.lunar.toolchain.ui

import com.intellij.serviceContainer.AlreadyDisposedException
import net.internetisalie.lunar.toolchain.registry.LuaToolchainChange
import net.internetisalie.lunar.toolchain.registry.LuaToolchainEvent
import net.internetisalie.lunar.toolchain.registry.LuaToolchainListener

/**
 * Re-checks the Lua Environment widget's availability whenever a project gains or loses an
 * environment, since availability is "has at least one environment" (BUG-534). Registered under
 * `<applicationListeners>` because [LuaToolchainListener.TOPIC] is published on the application bus.
 * Events can arrive on pooled threads while the project closes; a project disposed between the
 * check and the service lookup has no widget left to re-check, so that failure is ignored.
 */
class LuaEnvWidgetAvailabilityListener : LuaToolchainListener {
    override fun toolchainChanged(event: LuaToolchainEvent) {
        val targetProject = event.project ?: return
        if (event.change !in AVAILABILITY_CHANGES || targetProject.isDisposed) return
        try {
            LuaEnvWidgetAvailability.getInstance(targetProject).recheck()
        } catch (_: AlreadyDisposedException) {
            return
        }
    }

    private companion object {
        val AVAILABILITY_CHANGES = setOf(LuaToolchainChange.ENVIRONMENT_ADDED, LuaToolchainChange.ENVIRONMENT_REMOVED)
    }
}
