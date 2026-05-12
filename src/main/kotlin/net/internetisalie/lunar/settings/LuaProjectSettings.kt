package net.internetisalie.lunar.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target

@Service(Service.Level.PROJECT)
@State(
    name = "LuaProjectSettings",
    storages = [Storage("lunar.xml")],
    category = SettingsCategory.PLUGINS,
)
class LuaProjectSettings: PersistentStateComponent<LuaProjectSettings.State> {
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
        var targetState: TargetState? = null
        var interpreter: LuaInterpreter? = null
        var sourcePath: String = PathConfiguration.DEFAULT_SOURCE_PATH

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
            }
            val version = PlatformVersionRegistry.findVersion(platform, versionLabel)
                ?: PlatformVersionRegistry.defaultVersion(platform)
                ?: throw IllegalStateException("No version found for platform $platform")
            return Target(platform, version)
        }

        fun getTarget(): Target {
            if (targetState == null) {
                val target = migrateFromLegacySettings()
                targetState = TargetState.from(target)
            }
            return targetState!!.toTarget() ?: Target.default()
        }

        fun setTarget(newTarget: Target) {
            targetState = TargetState.from(newTarget)
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
