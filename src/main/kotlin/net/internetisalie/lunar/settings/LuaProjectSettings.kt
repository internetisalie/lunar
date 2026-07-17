package net.internetisalie.lunar.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Property
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target

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

        /**
         * TOOLING-08-02: true when the user has explicitly pinned the platform target on the *Lua
         * Project* page. In Auto mode ([explicitTarget] == false) [LuaTargetSynchronizer] owns the
         * target and follows the resolved runtime. Defaults to false so an old `lunar.xml` with no
         * tag deserializes as Auto (clean-break policy, no migration).
         */
        var explicitTarget: Boolean = false
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
         * Per-project LuaRocks registry server override (ROCKS-06-02). When non-blank, takes
         * precedence over the application-level TOOLING-02 `luarocks.serverUrl` kind option.
         * Stored in `.idea/lunar.xml` so teams share the registry target via VCS.
         * An empty string means "use the app default (or none)".
         */
        var rocksServerUrl: String = ""

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
        // TOOLING-05 Phase 5: clean break — no legacy-field migration. Stale env/interpreter-mode
        // tags in an old `lunar.xml` are silently ignored by the XML serializer (design §3.7).
        myState = state
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
