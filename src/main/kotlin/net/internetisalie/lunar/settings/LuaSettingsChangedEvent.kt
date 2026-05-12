package net.internetisalie.lunar.settings

import com.intellij.util.messages.Topic

/**
 * Event fired when project-level Lua settings change (e.g., target is updated).
 *
 * Listeners can react to target changes by refreshing library indexes, clearing caches,
 * or re-running analyses with the new target's configuration.
 *
 * Usage:
 * ```kotlin
 * // Listen for settings changes
 * val connection = project.messageBus.connect()
 * connection.subscribe(LuaSettingsChangedEvent.TOPIC, object : LuaSettingsChangedListener {
 *     override fun onSettingsChanged() {
 *         // React to settings change
 *     }
 * })
 * ```
 */
interface LuaSettingsChangedListener {
    /**
     * Called when project settings have changed.
     */
    fun onSettingsChanged()

    companion object {
        val TOPIC: Topic<LuaSettingsChangedListener> = Topic.create(
            "lua.settings.changed",
            LuaSettingsChangedListener::class.java
        )
    }
}
