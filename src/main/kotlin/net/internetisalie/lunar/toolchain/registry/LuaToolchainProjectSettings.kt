package net.internetisalie.lunar.toolchain.registry

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import java.io.File

class LuaToolchainProjectState {
    var bindings: MutableMap<String, String> = HashMap()
    var environments: MutableList<LuaEnvironmentState> = mutableListOf()
    var activeEnvironmentId: String = ""
    var kindOptions: MutableMap<String, String> = HashMap()
}

@Service(Service.Level.PROJECT)
@State(
    name = "LuaToolchainProjectSettings",
    storages = [Storage("lunar.xml")],
    category = SettingsCategory.PLUGINS
)
class LuaToolchainProjectSettings(private val project: Project) :
    PersistentStateComponent<LuaToolchainProjectState> {

    private val stateLock = Any()
    private var myState = LuaToolchainProjectState()

    override fun getState(): LuaToolchainProjectState {
        return synchronized(stateLock) {
            myState
        }
    }

    override fun loadState(state: LuaToolchainProjectState) {
        synchronized(stateLock) {
            myState = state
        }
    }

    fun environments(): List<LuaEnvironmentState> {
        return synchronized(stateLock) {
            myState.environments.toList()
        }
    }

    fun activeEnvironment(): LuaEnvironmentState? {
        return synchronized(stateLock) {
            val activeId = myState.activeEnvironmentId
            if (activeId.isBlank()) {
                null
            } else {
                myState.environments.firstOrNull { it.id == activeId }
            }
        }
    }

    companion object {
        fun getInstance(project: Project): LuaToolchainProjectSettings {
            return project.getService(LuaToolchainProjectSettings::class.java)
        }

        fun normalizeDir(directory: String): String =
            FileUtil.toCanonicalPath(File(directory).absolutePath)
    }
}
