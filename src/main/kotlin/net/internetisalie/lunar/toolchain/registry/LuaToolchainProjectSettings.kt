package net.internetisalie.lunar.toolchain.registry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import java.io.File
import java.util.UUID

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

    fun binding(kindId: String): String? {
        return synchronized(stateLock) {
            myState.bindings[kindId]
        }
    }

    fun setBinding(kindId: String, toolId: String?) {
        var changed = false
        synchronized(stateLock) {
            val current = myState.bindings[kindId]
            val invariantRemoved = enforceSingleRuntimeInvariant(myState.bindings, kindId, toolId)
            if (current != toolId) {
                if (toolId == null) {
                    myState.bindings.remove(kindId)
                } else {
                    myState.bindings[kindId] = toolId
                }
                changed = true
            }
            if (invariantRemoved) {
                changed = true
            }
        }
        if (changed) {
            publish(LuaToolchainChange.PROJECT_BINDING_CHANGED, kindId = kindId, toolId = toolId)
        }
    }

    fun upsertEnvironment(spec: LuaEnvironmentState): LuaEnvironmentState {
        val normalized = normalizeDir(spec.rootDir)
        var change: LuaToolchainChange? = null
        val resolved: LuaEnvironmentState
        synchronized(stateLock) {
            val existingIndex = myState.environments.indexOfFirst {
                it.id == spec.id || normalizeDir(it.rootDir) == normalized
            }
            if (existingIndex == -1) {
                resolved = spec.copy(id = spec.id.ifBlank { UUID.randomUUID().toString() })
                myState.environments.add(resolved)
                change = LuaToolchainChange.ENVIRONMENT_ADDED
            } else {
                val existing = myState.environments[existingIndex]
                val merged = spec.copy(id = existing.id.ifBlank { spec.id })
                resolved = merged
                if (merged != existing) {
                    myState.environments[existingIndex] = merged
                    change = LuaToolchainChange.ENVIRONMENT_UPDATED
                }
            }
        }
        change?.let { publish(it, environmentId = resolved.id) }
        return resolved
    }

    fun upsertEnvironmentAndActivate(spec: LuaEnvironmentState): LuaEnvironmentState {
        val resolved = upsertEnvironment(spec)
        var activated = false
        synchronized(stateLock) {
            if (myState.activeEnvironmentId != resolved.id) {
                myState.activeEnvironmentId = resolved.id
                activated = true
            }
        }
        if (activated) {
            publish(LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED, environmentId = resolved.id)
        }
        return resolved
    }

    fun activateEnvironment(envId: String): Boolean {
        var activated = false
        val result = synchronized(stateLock) {
            val known = myState.environments.any { it.id == envId }
            if (!known) {
                false
            } else {
                if (myState.activeEnvironmentId != envId) {
                    myState.activeEnvironmentId = envId
                    activated = true
                }
                true
            }
        }
        if (activated) {
            publish(LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED, environmentId = envId)
        }
        return result
    }

    fun deactivateEnvironment() {
        var deactivated = false
        synchronized(stateLock) {
            if (myState.activeEnvironmentId.isNotEmpty()) {
                myState.activeEnvironmentId = ""
                deactivated = true
            }
        }
        if (deactivated) {
            publish(LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED, environmentId = null)
        }
    }

    fun removeEnvironment(envId: String, deleteDir: Boolean) {
        val removal = removeEnvironmentRecord(envId) ?: return
        if (removal.wasActive) {
            publish(LuaToolchainChange.ACTIVE_ENVIRONMENT_CHANGED, environmentId = null)
        }
        LuaToolchainRegistry.getInstance().unregisterByEnvironment(envId)
        publish(LuaToolchainChange.ENVIRONMENT_REMOVED, environmentId = envId)
        if (deleteDir) {
            val rootDir = removal.rootDir
            ApplicationManager.getApplication().executeOnPooledThread {
                FileUtil.delete(File(rootDir))
            }
        }
    }

    fun setKindOption(key: String, value: String?) {
        var changed = false
        synchronized(stateLock) {
            val current = myState.kindOptions[key]
            if (value == null || value.isBlank()) {
                if (current != null) {
                    myState.kindOptions.remove(key)
                    changed = true
                }
            } else if (current != value) {
                myState.kindOptions[key] = value
                changed = true
            }
        }
        if (changed) {
            publish(LuaToolchainChange.KIND_OPTION_CHANGED, optionKey = key)
        }
    }

    fun effectiveKindOption(key: String): String {
        val projectValue = synchronized(stateLock) { myState.kindOptions[key] }?.trim()
        if (!projectValue.isNullOrBlank()) return projectValue
        val appValue = LuaToolchainRegistry.getInstance().kindOption(key).trim()
        if (appValue.isNotBlank()) return appValue
        return ""
    }

    private data class EnvironmentRemoval(val wasActive: Boolean, val rootDir: String)

    private fun removeEnvironmentRecord(envId: String): EnvironmentRemoval? {
        return synchronized(stateLock) {
            val env = myState.environments.firstOrNull { it.id == envId } ?: return@synchronized null
            val wasActive = myState.activeEnvironmentId == envId
            val rootDir = env.rootDir
            myState.environments.remove(env)
            if (wasActive) {
                myState.activeEnvironmentId = ""
            }
            EnvironmentRemoval(wasActive, rootDir)
        }
    }

    private fun publish(
        change: LuaToolchainChange,
        kindId: String? = null,
        toolId: String? = null,
        environmentId: String? = null,
        optionKey: String? = null
    ) {
        ApplicationManager.getApplication().messageBus.syncPublisher(LuaToolchainListener.TOPIC)
            .toolchainChanged(
                LuaToolchainEvent(
                    change = change,
                    project = project,
                    kindId = kindId,
                    toolId = toolId,
                    environmentId = environmentId,
                    optionKey = optionKey
                )
            )
    }

    companion object {
        fun getInstance(project: Project): LuaToolchainProjectSettings {
            return project.getService(LuaToolchainProjectSettings::class.java)
        }

        fun normalizeDir(directory: String): String =
            FileUtil.toCanonicalPath(File(directory).absolutePath)
    }
}
