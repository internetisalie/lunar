package net.internetisalie.lunar.toolchain.registry

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

const val TOPIC_ID = "lunar.toolchain.changed"

enum class LuaToolchainChange {
    TOOL_REGISTERED,
    TOOL_UPDATED,
    TOOL_REMOVED,
    GLOBAL_BINDING_CHANGED,
    PROJECT_BINDING_CHANGED,
    ENVIRONMENT_ADDED,
    ENVIRONMENT_UPDATED,
    ENVIRONMENT_REMOVED,
    ACTIVE_ENVIRONMENT_CHANGED,
    KIND_OPTION_CHANGED
}

data class LuaToolchainEvent(
    val change: LuaToolchainChange,
    val project: Project?,
    val kindId: String? = null,
    val toolId: String? = null,
    val environmentId: String? = null,
    val optionKey: String? = null
)

interface LuaToolchainListener {
    fun toolchainChanged(event: LuaToolchainEvent)

    companion object {
        val TOPIC: Topic<LuaToolchainListener> = Topic.create(
            TOPIC_ID,
            LuaToolchainListener::class.java
        )
    }
}
