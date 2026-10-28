package net.internetisalie.lunar.corpus

import com.intellij.openapi.project.Project
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings

/** The settings [CorpusTarget.pin] replaced, so [CorpusTarget.restore] can put them back. */
data class SavedTarget(
    val target: Target?,
    val languageLevel: LuaLanguageLevel,
    val explicitTarget: Boolean,
)

/**
 * Pins a corpus member to its target — the manifest's `target`, else the Standard target of its
 * level — not just the level (BUG-515, MAINT-39).
 *
 * The stdlib root follows [LuaProjectSettings.State.getTarget], which derives a target from
 * `languageLevel` only while none is stored. The shared light project already stores one, so
 * writing the level alone left every `LUA51` member measured against `runtime/standard/lua-5.4`.
 *
 * Runs on the test's EDT: the settings broadcast reloads the platform library in a write action.
 */
object CorpusTarget {
    fun pin(
        project: Project,
        level: LuaLanguageLevel,
    ): SavedTarget = pin(project, standardTarget(level))

    fun pin(
        project: Project,
        target: Target,
    ): SavedTarget {
        val settings = LuaProjectSettings.getInstance(project)
        val state = settings.state
        val saved = SavedTarget(state.target?.toTarget(), state.languageLevel, state.explicitTarget)
        // Explicit, so LuaTargetSynchronizer.recompute stops queueing moves to the builder's runtime.
        state.explicitTarget = true
        apply(project, target)
        return saved
    }

    /** The manifest's `target` when it names one, else the Standard target of its `luaLevel`. */
    fun memberTarget(entry: CorpusEntry): Target = entry.target ?: standardTarget(entry.luaLevel)

    fun restore(
        project: Project,
        saved: SavedTarget,
    ) {
        try {
            apply(project, saved.target ?: standardTarget(saved.languageLevel))
        } finally {
            val state = LuaProjectSettings.getInstance(project).state
            state.languageLevel = saved.languageLevel
            state.explicitTarget = saved.explicitTarget
        }
    }

    fun standardTarget(level: LuaLanguageLevel): Target {
        val version =
            requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, level.version)) {
                "No Standard platform version is registered for $level"
            }
        return Target(LuaPlatform.STANDARD, version)
    }

    private fun apply(
        project: Project,
        target: Target,
    ) {
        CorpusDefinitionLibraries.requireSettingsSubscriber(project)
        LuaProjectSettings.getInstance(project).setTargetAndNotify(target)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }
}
