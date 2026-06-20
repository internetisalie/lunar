package net.internetisalie.lunar.tool

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.settings.LuaApplicationSettings
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.io.File

/**
 * Application-level service that manages the lifecycle of registered [LuaTool] instances.
 *
 * Responsibilities (TOOL-01):
 * - [registerTool]: validate a binary at a given path, extract version info, and persist it.
 * - [unregisterTool]: remove a tool from the global inventory by UUID.
 * - [getTools]: return all registered tools with a lazy existence re-validation pass.
 * - [autoDiscover]: run [LuaToolDiscoveryService] and register any newly found tools.
 *
 * **Threading:** all methods that touch the file system or run subprocesses must be called
 * from a background thread (never the EDT). Settings mutation is safe because
 * `PersistentStateComponent` access is synchronised by the platform.
 *
 * TOOL-02 will extend this service to support project-level binding; TOOL-03 will add
 * scheduled health checks.  The public API is intentionally open.
 */
@Service(Service.Level.APP)
class LuaToolManager {

    private val LOG = logger<LuaToolManager>()

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Registers the binary at [path] in the global tool inventory.
     *
     * Validation steps:
     * 1. File must exist and be executable.
     * 2. Tool type is inferred from the binary name; if it cannot be inferred,
     *    the caller may supply [hintType].
     * 3. Version is extracted via [LuaToolValidator.extractVersion].
     * 4. Duplicate paths are deduplicated — re-registering an already-known path
     *    refreshes the entry in place.
     *
     * @return The registered (or refreshed) [LuaTool], or `null` if validation failed.
     */
    fun registerTool(path: String, hintType: LuaToolType? = null): LuaTool? {
        val file = File(path)
        if (!file.exists() || !file.isFile || !file.canExecute()) {
            LOG.warn("Cannot register tool at '$path': file not found or not executable")
            return null
        }

        val type = hintType ?: inferType(file) ?: run {
            LOG.warn("Cannot register tool at '$path': unknown tool type (use hintType)")
            return null
        }

        val version = LuaToolValidator.extractVersion(path, type) ?: ""
        val luaVersion = LuaToolValidator.detectLuaVersion(path, type)
        val isValid = version.isNotEmpty()
        val displayName = displayNameFor(type)

        val inventory = LuaApplicationSettings.instance.state.toolInventory

        // Refresh existing entry if path already registered
        val existing = inventory.firstOrNull { it.path == path }
        if (existing != null) {
            existing.version = version
            existing.luaVersion = luaVersion
            existing.isValid = isValid
            existing.name = displayName
            LOG.debug("[$type] refreshed existing tool at '$path' (version=$version)")
            return existing
        }

        val tool = LuaTool(
            type = type,
            name = displayName,
            path = path,
            version = version,
            luaVersion = luaVersion,
            isValid = isValid,
        )
        inventory.add(tool)
        LOG.debug("[$type] registered new tool at '$path' (version=$version, valid=$isValid)")
        return tool
    }

    /**
     * Removes the tool with the given [id] from the global inventory.
     *
     * @return `true` if a tool was removed; `false` if no tool with that id existed.
     */
    fun unregisterTool(id: String): Boolean {
        val inventory = LuaApplicationSettings.instance.state.toolInventory
        val removed = inventory.removeIf { it.id == id }
        if (removed) LOG.debug("Unregistered tool id=$id")
        return removed
    }

    /**
     * Returns all registered tools, re-validating file existence inline.
     *
     * Tools whose binary has been deleted since registration are marked `isValid = false`
     * but are NOT automatically removed (that is TOOL-03's health-monitor concern).
     */
    fun getTools(): List<LuaTool> {
        val inventory = LuaApplicationSettings.instance.state.toolInventory
        for (tool in inventory) {
            val file = File(tool.path)
            if (!file.exists() || !file.canExecute()) {
                if (tool.isValid) {
                    tool.isValid = false
                    LOG.debug("[${tool.type}] tool at '${tool.path}' is no longer accessible; marked invalid")
                }
            }
        }
        return inventory.toList()
    }

    /**
     * Returns registered tools filtered to those of the given [type].
     * Applies the same lazy re-validation as [getTools].
     */
    fun getTools(type: LuaToolType): List<LuaTool> = getTools().filter { it.type == type }

    /**
     * Runs auto-discovery via [LuaToolDiscoveryService] and registers any newly found tools.
     *
     * Already-registered paths are refreshed (version re-extracted). Unknown paths are added.
     *
     * @return The list of [LuaTool]s that were added or refreshed during this discovery run.
     */
    fun autoDiscover(): List<LuaTool> {
        val discovered = LuaToolDiscoveryService.discoverKnownTools()
        val results = mutableListOf<LuaTool>()
        for ((type, file) in discovered) {
            val tool = registerTool(file.absolutePath, hintType = type)
            if (tool != null) results += tool
        }
        LOG.debug("Auto-discovery complete: ${results.size} tool(s) registered/refreshed")
        return results
    }

    // ---------------------------------------------------------------------------
    // TOOL-02: project binding & resolution
    // ---------------------------------------------------------------------------

    /**
     * Resolves the effective tool for [type] for the given [project] using the precedence:
     *
     *   project binding > global default > first valid registered tool of that type.
     *
     * Each candidate id is re-validated against the live inventory: a bound id that no longer
     * resolves to a *valid* tool is skipped so resolution falls through to the next tier
     * (TOOL-02-06 fallback). [project] may be `null` to resolve global defaults only.
     *
     * **Threading:** safe on any thread; performs a lazy disk re-validation pass via [getTools].
     */
    fun getEffectiveTool(project: Project?, type: LuaToolType): LuaTool? {
        val valid = getTools(type).filter { it.isValid }
        if (valid.isEmpty()) return null

        val projectBinding = project
            ?.let { LuaProjectSettings.getInstance(it).state.projectToolBindings[type.name] }
            ?.let { id -> valid.firstOrNull { it.id == id } }
        if (projectBinding != null) return projectBinding

        val globalBinding = LuaApplicationSettings.instance.state.globalToolBindings[type.name]
            ?.let { id -> valid.firstOrNull { it.id == id } }
        if (globalBinding != null) return globalBinding

        return valid.first()
    }

    /**
     * Returns the effective valid tool for every [LuaToolType] resolvable in [project]
     * (one per type, de-duplicated). Used by [LuaTerminalEnvironmentService] and the
     * run-config PATH patcher to compute the tool-directory set.
     */
    fun getAllValidTools(project: Project?): List<LuaTool> =
        LuaToolType.entries.mapNotNull { getEffectiveTool(project, it) }

    /** Binds the global-default tool for [type] (or clears it when [id] is `null`). */
    fun setGlobalBinding(type: LuaToolType, id: String?) {
        val bindings = LuaApplicationSettings.instance.state.globalToolBindings
        if (id == null) bindings.remove(type.name) else bindings[type.name] = id
    }

    /** Returns the globally-bound tool for [type], if one is bound and still valid. */
    fun getGlobalBinding(type: LuaToolType): LuaTool? {
        val id = LuaApplicationSettings.instance.state.globalToolBindings[type.name] ?: return null
        return getTools(type).firstOrNull { it.id == id && it.isValid }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    /** Infers [LuaToolType] from the binary's filename. Returns `null` if unrecognised. */
    private fun inferType(file: File): LuaToolType? {
        val base = file.name.lowercase()
            .removeSuffix(".exe")
            .removeSuffix(".bat")
            .removeSuffix(".cmd")
        return when {
            base == "luarocks" -> LuaToolType.LUAROCKS
            base == "luacheck" -> LuaToolType.LUACHECK
            base == "stylua"   -> LuaToolType.STYLUA
            base == "luacov"   -> LuaToolType.LUACOV
            base == "busted"   -> LuaToolType.BUSTED
            else               -> null
        }
    }

    private fun displayNameFor(type: LuaToolType): String = when (type) {
        LuaToolType.LUAROCKS -> "LuaRocks"
        LuaToolType.LUACHECK -> "luacheck"
        LuaToolType.STYLUA   -> "StyLua"
        LuaToolType.LUACOV   -> "LuaCov"
        LuaToolType.BUSTED   -> "Busted"
    }

    // ---------------------------------------------------------------------------
    // Companion
    // ---------------------------------------------------------------------------

    companion object {
        @JvmStatic
        fun getInstance(): LuaToolManager =
            ApplicationManager.getApplication().getService(LuaToolManager::class.java)
    }
}
