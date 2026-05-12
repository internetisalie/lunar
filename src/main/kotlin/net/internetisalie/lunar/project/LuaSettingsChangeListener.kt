package net.internetisalie.lunar.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.settings.LuaSettingsChangedListener

/**
 * Listens for Lua settings changes and refreshes the platform library index.
 *
 * When the project target changes (e.g., switching from Lua 5.1 to Lua 5.4),
 * this listener reacts by reloading the platform library and invalidating caches.
 */
@Service(Service.Level.PROJECT)
class LuaSettingsChangeListener(private val project: Project) : LuaSettingsChangedListener {

    init {
        project.messageBus.connect().subscribe(LuaSettingsChangedListener.TOPIC, this)
    }

    override fun onSettingsChanged() {
        // Reload platform libraries when settings change
        // This invalidates caches and rescans dependencies
        PlatformLibraryIndex.reload()
    }
}
