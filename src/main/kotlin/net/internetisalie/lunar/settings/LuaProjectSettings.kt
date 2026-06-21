package net.internetisalie.lunar.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Property
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import com.intellij.openapi.project.ProjectManager

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
        @Deprecated("Use target.platform instead", replaceWith = ReplaceWith("target?.platform"))
        var platform : LuaPlatform = LuaPlatform.STANDARD
        @Property(surroundWithTag = false)
        var target: TargetState? = null
        var interpreter: LuaInterpreter? = null
        var sourcePath: String = PathConfiguration.DEFAULT_SOURCE_PATH
        var suppressUnderscorePrefixedGlobals: Boolean = true
        var additionalGlobals: MutableList<String> = mutableListOf()
        var showAutoImportHints: Boolean = true
        var autoImportStyle: AutoImportStyle = AutoImportStyle.AUTO_DETECT

        /**
         * Per-project tool bindings (TOOL-02): maps a [net.internetisalie.lunar.tool.LuaToolType]
         * name to the bound [net.internetisalie.lunar.tool.LuaTool.id]. Overrides the global
         * default. Stored in `.idea/lunar.xml` so teams can share project tool selections via VCS.
         * Keyed by the enum's `name` for stable XML serialization.
         */
        var projectToolBindings: MutableMap<String, String> = HashMap()

        fun expandSourcePath(project : Project) : String {
            return sourcePath.trim(' ').expandMacros(project)
        }

        private fun migrateFromLegacySettings(): Target {
            val versionLabel = when (languageLevel) {
                LuaLanguageLevel.LUA50 -> "5.0"  // Lua 5.0 - not in standard registry, use default
                LuaLanguageLevel.LUA51 -> "5.1"
                LuaLanguageLevel.LUA52 -> "5.2"
                LuaLanguageLevel.LUA53 -> "5.3"
                LuaLanguageLevel.LUA54 -> "5.4"
                LuaLanguageLevel.LUA55 -> "5.5"
            }
            val version = PlatformVersionRegistry.findVersion(platform, versionLabel)
                ?: PlatformVersionRegistry.defaultVersion(platform)
                ?: throw IllegalStateException("No version found for platform $platform")
            return Target(platform, version)
        }

        fun getTarget(): Target {
            if (target == null) {
                val t = migrateFromLegacySettings()
                setTarget(t)
            }
            return target!!.toTarget() ?: Target.default()
        }

        fun setTarget(newTarget: Target) {
            target = TargetState.from(newTarget)
            platform = newTarget.platform
            languageLevel = newTarget.getImplicitLanguageLevel()
        }
    }

    private var myState = State()

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
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
