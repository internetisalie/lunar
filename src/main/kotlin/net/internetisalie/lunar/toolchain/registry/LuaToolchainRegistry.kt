package net.internetisalie.lunar.toolchain.registry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.toolchain.discovery.LuaToolDiscovery
import net.internetisalie.lunar.toolchain.model.*
import net.internetisalie.lunar.toolchain.probe.LuaToolProbe
import net.internetisalie.lunar.toolchain.probe.LuaToolProbeResult

private val LOG = logger<LuaToolchainRegistry>()

enum class ProbeStatus {
    NEVER,
    OK,
    FAILED
}

class RegisteredToolState {
    var id: String = ""
    var kindId: String = ""
    var path: String = ""
    var version: String = ""
    var luaVersion: String = ""
    var product: String = ""
    var runtimeVersion: String = ""
    var languageLevel: String = ""
    var platform: String = ""
    var banner: String = ""
    var origin: Origin = Origin.MANUAL
    var environmentId: String = ""
    var fileExists: Boolean = false
    var executable: Boolean = false
    var probeStatus: ProbeStatus = ProbeStatus.NEVER
    var probedAtMtime: Long = 0L
    var reason: String = ""
}

class LuaToolchainAppState {
    var tools: MutableList<RegisteredToolState> = mutableListOf()
    var globalBindings: MutableMap<String, String> = HashMap()
    var kindOptions: MutableMap<String, String> = HashMap()
}

@Service(Service.Level.APP)
@State(
    name = "LuaToolchainRegistry",
    storages = [Storage("lunar.xml")],
    category = SettingsCategory.PLUGINS
)
class LuaToolchainRegistry : PersistentStateComponent<LuaToolchainAppState> {

    private val stateLock = Any()
    private var myState = LuaToolchainAppState()

    override fun getState(): LuaToolchainAppState {
        return synchronized(stateLock) {
            myState
        }
    }

    override fun loadState(state: LuaToolchainAppState) {
        synchronized(stateLock) {
            myState = state
        }
    }

    fun tools(): List<LuaRegisteredTool> {
        return synchronized(stateLock) {
            myState.tools.map { it.toModel() }
        }
    }

    fun toolsOfKind(kindId: String): List<LuaRegisteredTool> {
        return synchronized(stateLock) {
            myState.tools.filter { it.kindId == kindId }.map { it.toModel() }
        }
    }

    fun tool(id: String): LuaRegisteredTool? {
        return synchronized(stateLock) {
            myState.tools.firstOrNull { it.id == id }?.toModel()
        }
    }

    fun findByPath(path: String): LuaRegisteredTool? {
        return synchronized(stateLock) {
            myState.tools.firstOrNull { it.path == path }?.toModel() ?: run {
                val canonical = try { java.io.File(path).canonicalPath } catch (e: Exception) { path }
                myState.tools.firstOrNull {
                    val c = try { java.io.File(it.path).canonicalPath } catch (e: Exception) { it.path }
                    c == canonical
                }?.toModel()
            }
        }
    }

    fun globalBindings(): Map<String, String> {
        return synchronized(stateLock) {
            HashMap(myState.globalBindings)
        }
    }

    fun kindOption(key: String): String? {
        return synchronized(stateLock) {
            myState.kindOptions[key]
        }
    }

    fun setGlobalBinding(kindId: String, toolId: String?) {
        var changed = false
        synchronized(stateLock) {
            val current = myState.globalBindings[kindId]
            if (current != toolId) {
                if (toolId == null) {
                    myState.globalBindings.remove(kindId)
                } else {
                    myState.globalBindings[kindId] = toolId
                }
                changed = true
            }
        }
        if (changed) {
            ApplicationManager.getApplication().messageBus.syncPublisher(LuaToolchainListener.TOPIC)
                .toolchainChanged(
                    LuaToolchainEvent(
                        change = LuaToolchainChange.GLOBAL_BINDING_CHANGED,
                        project = null,
                        kindId = kindId,
                        toolId = toolId
                    )
                )
        }
    }

    fun setKindOption(key: String, value: String?) {
        var changed = false
        synchronized(stateLock) {
            val current = myState.kindOptions[key]
            if (current != value) {
                if (value == null) {
                    myState.kindOptions.remove(key)
                } else {
                    myState.kindOptions[key] = value
                }
                changed = true
            }
        }
        if (changed) {
            ApplicationManager.getApplication().messageBus.syncPublisher(LuaToolchainListener.TOPIC)
                .toolchainChanged(
                    LuaToolchainEvent(
                        change = LuaToolchainChange.KIND_OPTION_CHANGED,
                        project = null,
                        optionKey = key
                    )
                )
        }
    }

    fun registerTool(
        path: String,
        kindIdHint: String? = null,
        origin: Origin = Origin.MANUAL,
        environmentId: String? = null
    ): LuaRegisteredTool? {
        ApplicationManager.getApplication().assertIsNonDispatchThread()

        val file = java.io.File(path)
        val kind = kindIdHint?.let { LuaToolKindRegistry.findById(it) }
            ?: LuaToolKindRegistry.inferKind(file.name)

        if (kind == null) {
            LOG.warn("Cannot register tool at '$path': unknown tool kind (use kindIdHint)")
            return null
        }

        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val result = LuaToolProbe.getInstance().probe(kind, java.nio.file.Path.of(path))
        val health = deriveHealth(file, result)

        var change: LuaToolchainChange? = null
        var registeredTool: LuaRegisteredTool? = null

        synchronized(stateLock) {
            val existing = myState.tools.firstOrNull {
                val c = runCatching { java.io.File(it.path).canonicalPath }.getOrDefault(it.path)
                c == canonical && it.kindId == kind.id
            }

            if (existing != null) {
                val existingModel = existing.toModel()
                if (existingModel.version != result.version ||
                    existingModel.luaVersion != result.luaVersion ||
                    existingModel.health != health ||
                    existingModel.runtime != result.runtime
                ) {
                    existing.version = result.version ?: ""
                    existing.luaVersion = result.luaVersion ?: ""
                    existing.fileExists = health.fileExists
                    existing.executable = health.executable
                    existing.probeStatus = when (health.probeOk) {
                        null -> ProbeStatus.NEVER
                        true -> ProbeStatus.OK
                        false -> ProbeStatus.FAILED
                    }
                    existing.probedAtMtime = health.probedAtMtime ?: 0L
                    existing.reason = health.reason ?: ""

                    val rt = result.runtime
                    if (rt != null) {
                        existing.product = rt.product
                        existing.runtimeVersion = rt.version
                        existing.languageLevel = rt.languageLevel.name
                        existing.platform = rt.platform.name
                        existing.banner = rt.banner
                    } else {
                        existing.product = ""
                        existing.runtimeVersion = ""
                        existing.languageLevel = ""
                        existing.platform = ""
                        existing.banner = ""
                    }
                    change = LuaToolchainChange.TOOL_UPDATED
                }
                registeredTool = existing.toModel()
            } else {
                val state = RegisteredToolState().apply {
                    id = java.util.UUID.randomUUID().toString()
                    kindId = kind.id
                    this.path = file.absolutePath
                    version = result.version ?: ""
                    luaVersion = result.luaVersion ?: ""
                    this.origin = origin
                    this.environmentId = environmentId ?: ""
                    fileExists = health.fileExists
                    executable = health.executable
                    probeStatus = when (health.probeOk) {
                        null -> ProbeStatus.NEVER
                        true -> ProbeStatus.OK
                        false -> ProbeStatus.FAILED
                    }
                    probedAtMtime = health.probedAtMtime ?: 0L
                    reason = health.reason ?: ""

                    val rt = result.runtime
                    if (rt != null) {
                        product = rt.product
                        runtimeVersion = rt.version
                        languageLevel = rt.languageLevel.name
                        platform = rt.platform.name
                        banner = rt.banner
                    }
                }
                myState.tools.add(state)
                change = LuaToolchainChange.TOOL_REGISTERED
                registeredTool = state.toModel()
            }
        }

        if (change != null && registeredTool != null) {
            ApplicationManager.getApplication().messageBus.syncPublisher(LuaToolchainListener.TOPIC)
                .toolchainChanged(
                    LuaToolchainEvent(
                        change = change,
                        project = null,
                        kindId = kind.id,
                        toolId = registeredTool.id
                    )
                )
        }

        return registeredTool
    }

    fun registerProvisioned(tool: LuaRegisteredTool) {
        var change: LuaToolchainChange? = null
        val toolId = tool.id
        val kindId = tool.kindId
        val canonical = try { java.io.File(tool.path).canonicalPath } catch (e: Exception) { tool.path }
        synchronized(stateLock) {
            val existing = myState.tools.firstOrNull { it.id == toolId }
                ?: myState.tools.firstOrNull {
                    val c = try { java.io.File(it.path).canonicalPath } catch (e: Exception) { it.path }
                    c == canonical && it.kindId == kindId
                }
            if (existing != null) {
                val existingModel = existing.toModel()
                if (existingModel != tool) {
                    val state = tool.toState()
                    existing.version = state.version
                    existing.luaVersion = state.luaVersion
                    existing.product = state.product
                    existing.runtimeVersion = state.runtimeVersion
                    existing.languageLevel = state.languageLevel
                    existing.platform = state.platform
                    existing.banner = state.banner
                    existing.origin = state.origin
                    existing.environmentId = state.environmentId
                    existing.fileExists = state.fileExists
                    existing.executable = state.executable
                    existing.probeStatus = state.probeStatus
                    existing.probedAtMtime = state.probedAtMtime
                    existing.reason = state.reason
                    change = LuaToolchainChange.TOOL_UPDATED
                }
            } else {
                myState.tools.add(tool.toState())
                change = LuaToolchainChange.TOOL_REGISTERED
            }
        }
        if (change != null) {
            ApplicationManager.getApplication().messageBus.syncPublisher(LuaToolchainListener.TOPIC)
                .toolchainChanged(
                    LuaToolchainEvent(
                        change = change,
                        project = null,
                        kindId = kindId,
                        toolId = toolId
                    )
                )
        }
    }

    fun unregisterTool(id: String): Boolean {
        var kindId: String? = null
        var removed = false
        synchronized(stateLock) {
            val idx = myState.tools.indexOfFirst { it.id == id }
            if (idx != -1) {
                kindId = myState.tools[idx].kindId
                myState.tools.removeAt(idx)
                removed = true
            }
        }
        if (removed) {
            ApplicationManager.getApplication().messageBus.syncPublisher(LuaToolchainListener.TOPIC)
                .toolchainChanged(
                    LuaToolchainEvent(
                        change = LuaToolchainChange.TOOL_REMOVED,
                        project = null,
                        kindId = kindId,
                        toolId = id
                    )
                )
        }
        return removed
    }

    fun unregisterByEnvironment(environmentId: String) {
        val removedTools = mutableListOf<Pair<String, String>>()
        synchronized(stateLock) {
            val iterator = myState.tools.iterator()
            while (iterator.hasNext()) {
                val toolState = iterator.next()
                if (toolState.environmentId == environmentId) {
                    removedTools.add(toolState.id to toolState.kindId)
                    iterator.remove()
                }
            }
        }
        for ((toolId, kindId) in removedTools) {
            ApplicationManager.getApplication().messageBus.syncPublisher(LuaToolchainListener.TOPIC)
                .toolchainChanged(
                    LuaToolchainEvent(
                        change = LuaToolchainChange.TOOL_REMOVED,
                        project = null,
                        kindId = kindId,
                        toolId = toolId,
                        environmentId = environmentId
                    )
                )
        }
    }

    fun refreshTool(id: String) {
        ApplicationManager.getApplication().assertIsNonDispatchThread()
        val (path, kindId) = synchronized(stateLock) {
            val tool = myState.tools.firstOrNull { it.id == id } ?: return
            tool.path to tool.kindId
        } ?: return

        val kind = LuaToolKindRegistry.findById(kindId) ?: return
        val result = LuaToolProbe.getInstance().probe(kind, java.nio.file.Path.of(path))
        val file = java.io.File(path)
        val health = deriveHealth(file, result)

        var changed = false
        synchronized(stateLock) {
            val existing = myState.tools.firstOrNull { it.id == id }
            if (existing != null) {
                val existingModel = existing.toModel()
                if (existingModel.version != result.version ||
                    existingModel.luaVersion != result.luaVersion ||
                    existingModel.health != health ||
                    existingModel.runtime != result.runtime
                ) {
                    existing.version = result.version ?: ""
                    existing.luaVersion = result.luaVersion ?: ""
                    existing.fileExists = health.fileExists
                    existing.executable = health.executable
                    existing.probeStatus = when (health.probeOk) {
                        null -> ProbeStatus.NEVER
                        true -> ProbeStatus.OK
                        false -> ProbeStatus.FAILED
                    }
                    existing.probedAtMtime = health.probedAtMtime ?: 0L
                    existing.reason = health.reason ?: ""

                    val rt = result.runtime
                    if (rt != null) {
                        existing.product = rt.product
                        existing.runtimeVersion = rt.version
                        existing.languageLevel = rt.languageLevel.name
                        existing.platform = rt.platform.name
                        existing.banner = rt.banner
                    } else {
                        existing.product = ""
                        existing.runtimeVersion = ""
                        existing.languageLevel = ""
                        existing.platform = ""
                        existing.banner = ""
                    }
                    changed = true
                }
            }
        }
        if (changed) {
            ApplicationManager.getApplication().messageBus.syncPublisher(LuaToolchainListener.TOPIC)
                .toolchainChanged(
                    LuaToolchainEvent(
                        change = LuaToolchainChange.TOOL_UPDATED,
                        project = null,
                        kindId = kindId,
                        toolId = id
                    )
                )
        }
    }

    fun updateToolCheck(
        toolId: String,
        health: LuaToolHealth,
        version: String?,
        luaVersion: String?,
        runtime: LuaRuntimeInfo?
    ) {
        var kindId: String? = null
        var changed = false
        synchronized(stateLock) {
            val existing = myState.tools.firstOrNull { it.id == toolId }
            if (existing != null) {
                kindId = existing.kindId
                val existingModel = existing.toModel()
                if (existingModel.version != version ||
                    existingModel.luaVersion != luaVersion ||
                    existingModel.health != health ||
                    existingModel.runtime != runtime
                ) {
                    existing.version = version ?: ""
                    existing.luaVersion = luaVersion ?: ""
                    existing.fileExists = health.fileExists
                    existing.executable = health.executable
                    existing.probeStatus = when (health.probeOk) {
                        null -> ProbeStatus.NEVER
                        true -> ProbeStatus.OK
                        false -> ProbeStatus.FAILED
                    }
                    existing.probedAtMtime = health.probedAtMtime ?: 0L
                    existing.reason = health.reason ?: ""

                    if (runtime != null) {
                        existing.product = runtime.product
                        existing.runtimeVersion = runtime.version
                        existing.languageLevel = runtime.languageLevel.name
                        existing.platform = runtime.platform.name
                        existing.banner = runtime.banner
                    } else {
                        existing.product = ""
                        existing.runtimeVersion = ""
                        existing.languageLevel = ""
                        existing.platform = ""
                        existing.banner = ""
                    }
                    changed = true
                }
            }
        }
        if (changed) {
            ApplicationManager.getApplication().messageBus.syncPublisher(LuaToolchainListener.TOPIC)
                .toolchainChanged(
                    LuaToolchainEvent(
                        change = LuaToolchainChange.TOOL_UPDATED,
                        project = null,
                        kindId = kindId,
                        toolId = toolId
                    )
                )
        }
    }

    fun autoDiscover() {
        autoDiscover(emptyList())
    }

    fun autoDiscover(extraRoots: List<java.nio.file.Path>) {
        ApplicationManager.getApplication().assertIsNonDispatchThread()
        val discovered = LuaToolDiscovery.discoverAll(extraRoots = extraRoots)
        for (bin in discovered) {
            registerTool(
                path = bin.file.absolutePath,
                kindIdHint = bin.kind.id,
                origin = Origin.DISCOVERED
            )
        }
    }

    private fun deriveHealth(file: java.io.File, result: LuaToolProbeResult): LuaToolHealth {
        val fileExists = file.exists()
        val executable = file.canExecute()
        val probeOk = if (!fileExists || !executable) null else result.ok
        val probedAtMtime = if (fileExists) file.lastModified() else null
        val reason = result.failure
        return LuaToolHealth(
            fileExists = fileExists,
            executable = executable,
            probeOk = probeOk,
            probedAtMtime = probedAtMtime,
            reason = reason
        )
    }

    companion object {
        fun getInstance(): LuaToolchainRegistry {
            return ApplicationManager.getApplication().getService(LuaToolchainRegistry::class.java)
        }
    }
}

fun RegisteredToolState.toModel(): LuaRegisteredTool {
    val versionVal = version.takeIf { it.isNotEmpty() }
    val luaVersionVal = luaVersion.takeIf { it.isNotEmpty() }
    val envIdVal = environmentId.takeIf { it.isNotEmpty() }
    val reasonVal = reason.takeIf { it.isNotEmpty() }
    val mtimeVal = probedAtMtime.takeIf { it != 0L }

    val probeOkVal = when (probeStatus) {
        ProbeStatus.NEVER -> null
        ProbeStatus.OK -> true
        ProbeStatus.FAILED -> false
    }

    val healthVal = LuaToolHealth(
        fileExists = fileExists,
        executable = executable,
        probeOk = probeOkVal,
        probedAtMtime = mtimeVal,
        reason = reasonVal
    )

    val runtimeVal = if (product.isNotEmpty()) {
        val level = try {
            LuaLanguageLevel.valueOf(languageLevel)
        } catch (e: Exception) {
            LuaLanguageLevel.LUA54
        }
        val plat = try {
            LuaPlatform.valueOf(platform)
        } catch (e: Exception) {
            LuaPlatform.STANDARD
        }
        LuaRuntimeInfo(
            product = product,
            version = runtimeVersion,
            languageLevel = level,
            platform = plat,
            banner = banner
        )
    } else {
        null
    }

    return LuaRegisteredTool(
        id = id,
        kindId = kindId,
        path = path,
        version = versionVal,
        luaVersion = luaVersionVal,
        runtime = runtimeVal,
        origin = origin,
        environmentId = envIdVal,
        health = healthVal
    )
}

fun LuaRegisteredTool.toState(): RegisteredToolState {
    val state = RegisteredToolState()
    state.id = id
    state.kindId = kindId
    state.path = path
    state.version = version ?: ""
    state.luaVersion = luaVersion ?: ""
    state.origin = origin
    state.environmentId = environmentId ?: ""
    state.fileExists = health.fileExists
    state.executable = health.executable
    state.probeStatus = when (health.probeOk) {
        null -> ProbeStatus.NEVER
        true -> ProbeStatus.OK
        false -> ProbeStatus.FAILED
    }
    state.probedAtMtime = health.probedAtMtime ?: 0L
    state.reason = health.reason ?: ""

    val rt = runtime
    if (rt != null) {
        state.product = rt.product
        state.runtimeVersion = rt.version
        state.languageLevel = rt.languageLevel.name
        state.platform = rt.platform.name
        state.banner = rt.banner
    } else {
        state.product = ""
        state.runtimeVersion = ""
        state.languageLevel = ""
        state.platform = ""
        state.banner = ""
    }

    return state
}
