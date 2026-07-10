package net.internetisalie.lunar.redis.connection

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Project-level [PersistentStateComponent] holding the named connection list (design §2.5).
 *
 * State is stored in `.idea/lunar-redis.xml` (VCS-shareable). The persisted [ConnectionState] mirrors
 * [LuaRedisServerConnection] **without the secret** — the AUTH password lives in
 * [LuaRedisCredentialStore] (engineering-contract §4). [findById] is the public resolve-by-id seam
 * REDIS-02/REDIS-05 consume (risks-and-gaps "Public Seams"). Follows the `settings/LuaProjectSettings`
 * shape; holds no hard `Project`/`Editor`/`PsiFile` reference.
 */
@State(name = "LunarRedisConnections", storages = [Storage("lunar-redis.xml")])
@Service(Service.Level.PROJECT)
class LuaRedisConnectionSettings : PersistentStateComponent<LuaRedisConnectionSettings.State> {

    /** Root persisted element: the ordered list of connections. */
    class State {
        var connections: MutableList<ConnectionState> = mutableListOf()
    }

    /** XML-serializable mirror of [LuaRedisServerConnection] (no secret). */
    class ConnectionState {
        var id: String = ""
        var name: String = ""
        var host: String = "127.0.0.1"
        var port: Int = 6379
        var tls: Boolean = false
        var database: Int = 0
        var username: String? = null
        var provisioningKind: String = LuaRedisProvisioning.KIND_REMOTE
        var toolKindId: String? = null
        var dockerImage: String? = null
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /** All connections as immutable domain models, in persisted order. */
    fun connections(): List<LuaRedisServerConnection> = myState.connections.map { it.toModel() }

    /** Resolves a connection by its stable [id], or `null` when absent (design §2.5 seam). */
    fun findById(id: String): LuaRedisServerConnection? =
        myState.connections.firstOrNull { it.id == id }?.toModel()

    /** Inserts [connection], or replaces the existing entry with the same [LuaRedisServerConnection.id]. */
    fun upsert(connection: LuaRedisServerConnection) {
        val existingIndex = myState.connections.indexOfFirst { it.id == connection.id }
        val persisted = connection.toState()
        if (existingIndex >= 0) {
            myState.connections[existingIndex] = persisted
        } else {
            myState.connections.add(persisted)
        }
    }

    /** Removes the connection with [id], if present. */
    fun remove(id: String) {
        myState.connections.removeAll { it.id == id }
    }

    companion object {
        fun getInstance(project: Project): LuaRedisConnectionSettings = project.service()

        private fun ConnectionState.toModel(): LuaRedisServerConnection =
            LuaRedisServerConnection(
                id = id,
                name = name,
                host = host,
                port = port,
                tls = tls,
                database = database,
                username = username,
                provisioning = provisioningOf(this),
            )

        private fun provisioningOf(state: ConnectionState): LuaRedisProvisioning = when (state.provisioningKind) {
            LuaRedisProvisioning.KIND_LOCAL_BINARY ->
                LuaRedisProvisioning.LocalBinary(state.toolKindId ?: "redis-server")
            LuaRedisProvisioning.KIND_DOCKER ->
                LuaRedisProvisioning.Docker(state.dockerImage ?: "redis:8")
            else -> LuaRedisProvisioning.Remote
        }

        private fun LuaRedisServerConnection.toState(): ConnectionState {
            val state = ConnectionState()
            state.id = id
            state.name = name
            state.host = host
            state.port = port
            state.tls = tls
            state.database = database
            state.username = username
            applyProvisioning(state, provisioning)
            return state
        }

        private fun applyProvisioning(state: ConnectionState, provisioning: LuaRedisProvisioning) {
            when (provisioning) {
                is LuaRedisProvisioning.Remote -> {
                    state.provisioningKind = LuaRedisProvisioning.KIND_REMOTE
                }
                is LuaRedisProvisioning.LocalBinary -> {
                    state.provisioningKind = LuaRedisProvisioning.KIND_LOCAL_BINARY
                    state.toolKindId = provisioning.toolKindId
                }
                is LuaRedisProvisioning.Docker -> {
                    state.provisioningKind = LuaRedisProvisioning.KIND_DOCKER
                    state.dockerImage = provisioning.image
                }
            }
        }
    }
}
