package net.internetisalie.lunar.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.xmlb.annotations.Property
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.rocks.env.HererocksEnvBinder
import net.internetisalie.lunar.rocks.env.HererocksEnvState
import net.internetisalie.lunar.util.newProjectBackgroundTask
import java.util.UUID

@Service(Service.Level.PROJECT)
@State(
    name = "LuaProjectSettings",
    storages = [Storage("lunar.xml")],
    category = SettingsCategory.PLUGINS,
)
class LuaProjectSettings(private val project: Project? = null): PersistentStateComponent<LuaProjectSettings.State> {
    /**
     * XML-serializable wrapper for Target.
     * Used to persist target as {platform, version label} for deserialization via registry lookup.
     */
    class TargetState {
        var platform: LuaPlatform = LuaPlatform.STANDARD
        var versionLabel: String = "5.4"

        companion object {
            fun from(target: Target): TargetState {
                val state = TargetState()
                state.platform = target.platform
                state.versionLabel = target.version.label
                return state
            }
        }

        fun toTarget(): Target? {
            val version = PlatformVersionRegistry.findVersion(platform, versionLabel)
                ?: PlatformVersionRegistry.defaultVersion(platform)
            return version?.let { Target(platform, it) }
        }
    }
    class State {
        var languageLevel : LuaLanguageLevel = LuaLanguageLevel.LUA54
        @Property(surroundWithTag = false)
        var target: TargetState? = null
        var interpreter: LuaInterpreter? = null

        /**
         * Who owns the project interpreter + target (ROCKS-16). [InterpreterMode.EXPLICIT]: the user's
         * combo selections are authoritative and a hererocks bind/switch/unbind must not touch
         * [interpreter] or [target]. [InterpreterMode.HEREROCKS_MANAGED]: the active env drives both,
         * re-derived on every bind/switch; the user's manual choice is stashed in
         * [explicitInterpreter]/[explicitTarget] so it can be restored on unbind or mode-off.
         */
        var interpreterMode: InterpreterMode = InterpreterMode.EXPLICIT

        /**
         * One-shot guard for the [interpreterMode] migration (see [migrateInterpreterMode]). Existing
         * projects predate the field; `false` means "never chosen", so load-time can seed the mode from
         * whether an env is already bound. Set `true` once seeded so a later Explicit choice sticks.
         */
        var interpreterModeMigrated: Boolean = false

        /**
         * Non-destructive overlay of the user's explicit interpreter/target while
         * [interpreterMode] is [InterpreterMode.HEREROCKS_MANAGED]. Captured on entering Managed mode
         * and restored on unbind or on switching back to [InterpreterMode.EXPLICIT].
         */
        var explicitInterpreter: LuaInterpreter? = null
        @Property(surroundWithTag = false)
        var explicitTarget: TargetState? = null
        var sourcePath: String = PathConfiguration.DEFAULT_SOURCE_PATH
        var suppressUnderscorePrefixedGlobals: Boolean = true
        var additionalGlobals: MutableList<String> = mutableListOf()

        /**
         * Per-project rockspec membership override globs (ROCKS-09-07). When both lists are empty,
         * discovery uses the default recursive-minus-built-in-excludes behaviour. When non-empty,
         * [rockspecIncludeGlobs] acts as an allow-list and [rockspecExcludeGlobs] removes matches;
         * both AND with the built-in exclusions. Persisted in the existing `lunar.xml` storage.
         */
        var rockspecIncludeGlobs: MutableList<String> = mutableListOf()
        var rockspecExcludeGlobs: MutableList<String> = mutableListOf()

        var showAutoImportHints: Boolean = true
        var autoImportStyle: AutoImportStyle = AutoImportStyle.AUTO_DETECT

        /**
         * Per-project tool bindings (TOOL-02): maps a [net.internetisalie.lunar.tool.LuaToolType]
         * name to the bound [net.internetisalie.lunar.tool.LuaTool.id]. Overrides the global
         * default. Stored in `.idea/lunar.xml` so teams can share project tool selections via VCS.
         * Keyed by the enum's `name` for stable XML serialization.
         */
        var projectToolBindings: MutableMap<String, String> = HashMap()

        /**
         * Per-project LuaRocks registry server override (ROCKS-06-02). When non-blank, takes
         * precedence over the application-level [net.internetisalie.lunar.rocks.run.LuaRocksSettings.serverUrl].
         * Stored in `.idea/lunar.xml` so teams share the registry target via VCS.
         * An empty string means "use the app default (or none)".
         */
        var rocksServerUrl: String = ""

        /**
         * The hererocks-provisioned Lua environment descriptor for this project (ROCKS-14-01), or
         * `null` when none has been bound. Stored in `.idea/lunar.xml` so the env spec is
         * VCS-shared and upgrade/recreate stay reproducible across a team.
         */
        @Deprecated("ROCKS-15: migrated into hererocksEnvs on load; kept for back-compat read")
        var hererocksEnv: HererocksEnvState? = null

        /**
         * The set of hererocks-provisioned Lua environments for this project (ROCKS-15-01). The
         * ROCKS-14 single [hererocksEnv] is migrated into this list on [loadState]. Defaulted for
         * the XML serializer, so `var`/[MutableList] per the sanctioned serialization exception.
         */
        var hererocksEnvs: MutableList<HererocksEnvState> = mutableListOf()

        /**
         * Id of the active environment within [hererocksEnvs] (ROCKS-15-01). `""` or an id absent
         * from the list means "no active env" (app-fallback resolution, unchanged from ROCKS-14).
         */
        var activeEnvId: String = ""

        fun expandSourcePath(project : Project) : String {
            return sourcePath.trim(' ').expandMacros(project)
        }

        private fun buildDefaultTarget(): Target {
            val defaultPlatform = LuaPlatform.STANDARD
            val versionLabel = when (languageLevel) {
                LuaLanguageLevel.LUA50 -> "5.0"  // Lua 5.0 - not in standard registry, use default
                LuaLanguageLevel.LUA51 -> "5.1"
                LuaLanguageLevel.LUA52 -> "5.2"
                LuaLanguageLevel.LUA53 -> "5.3"
                LuaLanguageLevel.LUA54 -> "5.4"
                LuaLanguageLevel.LUA55 -> "5.5"
            }
            val version = PlatformVersionRegistry.findVersion(defaultPlatform, versionLabel)
                ?: PlatformVersionRegistry.defaultVersion(defaultPlatform)
                ?: throw IllegalStateException("No version found for platform $defaultPlatform")
            return Target(defaultPlatform, version)
        }

        fun getTarget(): Target {
            if (target == null) {
                val t = buildDefaultTarget()
                setTarget(t)
            }
            return target!!.toTarget() ?: Target.default()
        }

        fun setTarget(newTarget: Target) {
            target = TargetState.from(newTarget)
            languageLevel = newTarget.getImplicitLanguageLevel()
        }
    }

    private var myState = State()

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        migrateLegacyEnv(state)
        migrateInterpreterMode(state)
        myState = state
    }

    /**
     * Seeds [State.interpreterMode] for projects that predate the field (ROCKS-16). Runs after
     * [migrateLegacyEnv] so [State.activeEnvId] reflects any migrated ROCKS-14 env. A project that
     * already has a bound env defaults to [InterpreterMode.HEREROCKS_MANAGED] so today's implicit
     * "the env drives the interpreter" behaviour is retained; otherwise it defaults to
     * [InterpreterMode.EXPLICIT]. Idempotent via [State.interpreterModeMigrated].
     */
    private fun migrateInterpreterMode(state: State) {
        if (state.interpreterModeMigrated) return
        state.interpreterMode =
            if (state.activeEnvId.isNotBlank()) InterpreterMode.HEREROCKS_MANAGED
            else InterpreterMode.EXPLICIT
        state.interpreterModeMigrated = true
    }

    /**
     * One-time migration of the ROCKS-14 single [State.hererocksEnv] into the ROCKS-15
     * [State.hererocksEnvs] list (ROCKS-15-01, design §3.1). Idempotent: guarded by an id-absent
     * check; assigns a UUID when the legacy id is blank; only auto-selects the active env when
     * none is set; nulls out the consumed legacy field.
     */
    @Suppress("DEPRECATION")
    private fun migrateLegacyEnv(state: State) {
        val legacy = state.hererocksEnv ?: return
        if (state.hererocksEnvs.any { it.id == legacy.id }) return
        if (legacy.id.isBlank()) legacy.id = UUID.randomUUID().toString()
        state.hererocksEnvs.add(legacy)
        if (state.activeEnvId.isBlank()) state.activeEnvId = legacy.id
        state.hererocksEnv = null
    }

    /** Returns the full environment set (ROCKS-15-01, design §2.2). */
    fun resolveAllEnvs(): List<HererocksEnvState> = state.hererocksEnvs.toList()

    /** Returns the active environment, or `null` when none is selected (ROCKS-15-01). */
    fun activeEnv(): HererocksEnvState? =
        state.hererocksEnvs.firstOrNull { it.id == state.activeEnvId }

    /** Appends [spec] to the set when its id is not already present (ROCKS-15-05, design §2.2). */
    fun addEnv(spec: HererocksEnvState) {
        if (state.hererocksEnvs.none { it.id == spec.id }) state.hererocksEnvs.add(spec)
    }

    /** Removes [envId] from the set and clears [State.activeEnvId] if it was active (ROCKS-15). */
    fun removeEnv(envId: String) {
        state.hererocksEnvs.removeAll { it.id == envId }
        if (state.activeEnvId == envId) state.activeEnvId = ""
    }

    /**
     * Set-aware activation and the single live source of truth for env create/detect/switch/batch
     * (ROCKS-15 remediation, defect A). Upserts [spec] into [State.hererocksEnvs] — deduping by `id`
     * *and* by normalized [HererocksEnvState.directory] so a re-provision/detect of the same directory
     * never appends a twin — marks it active, then binds it via
     * [net.internetisalie.lunar.rocks.env.HererocksEnvBinder.bind] (interpreter + `LUAROCKS` tool +
     * the `LuaSettingsChangedListener.TOPIC` fire). Unlike the legacy path this surfaces the env in
     * the status-bar switcher immediately, without a project reload.
     */
    fun upsertAndActivate(project: Project, spec: HererocksEnvState) {
        val incomingDir = HererocksEnvBinder.normalizeDir(spec.directory)
        val existing = state.hererocksEnvs.firstOrNull {
            it.id == spec.id || HererocksEnvBinder.normalizeDir(it.directory) == incomingDir
        }
        val resolved = if (existing == null) {
            state.hererocksEnvs.add(spec)
            spec
        } else {
            val index = state.hererocksEnvs.indexOf(existing)
            val merged = spec.copy(id = existing.id.ifBlank { spec.id })
            state.hererocksEnvs[index] = merged
            merged
        }
        state.activeEnvId = resolved.id
        HererocksEnvBinder.bind(project, resolved)
    }

    /**
     * Binds [envId]'s environment via [net.internetisalie.lunar.rocks.env.HererocksEnvBinder.bind]
     * (which repoints the interpreter + `LUAROCKS` binding and fires
     * [LuaSettingsChangedListener.TOPIC]) and marks it active (ROCKS-15-02, design §3.2). Unknown
     * ids are a no-op; switching never re-provisions.
     */
    fun setActiveEnvAndNotify(project: Project, envId: String) {
        val target = state.hererocksEnvs.firstOrNull { it.id == envId } ?: return
        HererocksEnvBinder.bind(project, target)
        state.activeEnvId = envId
    }

    /**
     * Updates the target and fires a [LuaSettingsChangedEvent] to notify listeners.
     * This should be called instead of directly calling state.setTarget() to ensure
     * that all listeners are notified of the change.
     *
     * @param newTarget The new target to set
     */
    fun setTargetAndNotify(newTarget: Target) {
        state.setTarget(newTarget)
        project?.messageBus?.syncPublisher(LuaSettingsChangedListener.TOPIC)?.onSettingsChanged()
    }

    /**
     * Binds (or, when [toolId] is `null`, clears) the project-level tool for [typeName] and
     * notifies listeners via the shared [LuaSettingsChangedListener.TOPIC] so caches
     * (e.g. [net.internetisalie.lunar.tool.LuaTerminalEnvironmentService]) invalidate immediately.
     */
    fun setProjectToolBindingAndNotify(typeName: String, toolId: String?) {
        if (toolId == null) {
            state.projectToolBindings.remove(typeName)
        } else {
            state.projectToolBindings[typeName] = toolId
        }
        project?.messageBus?.syncPublisher(LuaSettingsChangedListener.TOPIC)?.onSettingsChanged()
    }

    /**
     * Sets (or clears when [interpreter] is `null`) the project interpreter and notifies listeners
     * via [LuaSettingsChangedListener.TOPIC] (ROCKS-14-04). Mirrors [setProjectToolBindingAndNotify]
     * so downstream caches invalidate immediately after a hererocks bind.
     */
    fun setInterpreterAndNotify(interpreter: LuaInterpreter?) {
        state.interpreter = interpreter
        project?.messageBus?.syncPublisher(LuaSettingsChangedListener.TOPIC)?.onSettingsChanged()
    }

    private fun notifyChanged() {
        project?.messageBus?.syncPublisher(LuaSettingsChangedListener.TOPIC)?.onSettingsChanged()
    }

    val interpreterMode: InterpreterMode
        get() = state.interpreterMode

    /**
     * Switches the project [InterpreterMode] and reconciles the interpreter/target overlay (ROCKS-16).
     *
     * → [InterpreterMode.HEREROCKS_MANAGED]: stashes the current explicit interpreter/target into the
     * overlay and, when an env is active, re-derives them from it by kicking a background
     * [HererocksEnvBinder.bind] (the `lua -v` probe must stay off the EDT; bind marshals its UI
     * mutation back internally).
     *
     * → [InterpreterMode.EXPLICIT]: restores the stashed interpreter/target (or project defaults when
     * none was stashed) and rebuilds platform libraries if the language level moved.
     *
     * No-op when [mode] already matches. Callers on the EDT (settings panel, unbind) are safe: the
     * only off-EDT work is delegated to the background bind.
     */
    fun setInterpreterModeAndNotify(project: Project, mode: InterpreterMode) {
        if (state.interpreterMode == mode) return
        when (mode) {
            InterpreterMode.HEREROCKS_MANAGED -> {
                state.explicitInterpreter = state.interpreter
                state.explicitTarget = state.target
                state.interpreterMode = mode
                val active = activeEnv()
                if (active != null) {
                    newProjectBackgroundTask("Applying Lua environment", project) {
                        HererocksEnvBinder.bind(project, active)
                    }.queue()
                } else {
                    notifyChanged()
                }
            }

            InterpreterMode.EXPLICIT -> {
                state.interpreterMode = mode
                restoreExplicitOverlay()
                notifyChanged()
            }
        }
    }

    /**
     * Restores [State.interpreter]/[State.target] from the explicit overlay (or project defaults when
     * none was stashed) and rebuilds platform libraries if the language level changed (ROCKS-16). Must
     * run on the EDT (see [PlatformLibraryIndex.reload]); does not fire a change event itself.
     */
    fun restoreExplicitOverlay() {
        val previousLevel = state.languageLevel
        state.interpreter = state.explicitInterpreter
        state.target = state.explicitTarget
        state.languageLevel = state.getTarget().getImplicitLanguageLevel()
        if (state.languageLevel != previousLevel) PlatformLibraryIndex.reload()
    }

    val suppressUnderscorePrefixedGlobals: Boolean
        get() = state.suppressUnderscorePrefixedGlobals

    val additionalGlobals: List<String>
        get() = state.additionalGlobals

    val showAutoImportHints: Boolean
        get() = state.showAutoImportHints

    val autoImportStyle: AutoImportStyle
        get() = state.autoImportStyle

    companion object {
        fun getInstance(project: Project): LuaProjectSettings {
            return project.getService(LuaProjectSettings::class.java)
        }
    }
}

fun String.expandMacros(project: Project) : String {
    return PathMacroManager
        .getInstance(project)
        .expandPath(this)
}

/**
 * User-configurable override for the auto-import template style (COMP-03-03).
 *
 * - [AUTO_DETECT]: inspect the target module for a root `return` to choose the template.
 * - [FORCE_LOCAL_ASSIGN]: always emit `local name = require("path")`.
 * - [FORCE_GLOBAL]: always emit a bare `require("path")`.
 */
enum class AutoImportStyle {
    AUTO_DETECT,
    FORCE_LOCAL_ASSIGN,
    FORCE_GLOBAL,
}

/**
 * Who owns the project interpreter + target (ROCKS-16).
 *
 * - [EXPLICIT]: the user's Interpreter/Platform/Version selections are authoritative; a hererocks
 *   bind/switch/unbind binds the `LUAROCKS` tool and tracks the active env but never touches the
 *   interpreter or target.
 * - [HEREROCKS_MANAGED]: the active hererocks env drives the interpreter and target (and thus the
 *   language level + platform libraries), re-derived on every bind/switch; the panel's Interpreter/
 *   Platform/Version controls become read-only derived views.
 */
enum class InterpreterMode {
    EXPLICIT,
    HEREROCKS_MANAGED,
}
