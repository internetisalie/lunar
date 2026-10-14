package net.internetisalie.lunar.corpus

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.impl.AnyPsiChangeListener
import com.intellij.psi.impl.PsiManagerImpl
import net.internetisalie.lunar.settings.LuaSettingsChangedListener

/**
 * What one definition-roots refresh did, observed by its effects (MAINT-37 design §2.6a): the
 * settings publishes, the roots change the subscriber's reload makes, and the physical PSI events
 * the reload, the resolve-cache drop and each dumb-mode entry and exit cause.
 */
data class RefreshObservation(
    val settingsBroadcasts: Int,
    val rootsTicks: Long,
    val physicalPsiChanges: Int,
)

/**
 * Counts a refresh's effects from [attach] to [observe]. Holds no [Project]; its subscriptions live
 * on one connection disposed with the parent [Disposable].
 */
class CorpusRefreshProbe private constructor(
    private val rootsAtAttach: Long,
) {
    private var settingsBroadcasts = 0
    private var physicalPsiChanges = 0

    fun observe(project: Project): RefreshObservation =
        RefreshObservation(
            settingsBroadcasts,
            ProjectRootModificationTracker.getInstance(project).modificationCount - rootsAtAttach,
            physicalPsiChanges,
        )

    companion object {
        fun attach(
            project: Project,
            parent: Disposable,
        ): CorpusRefreshProbe {
            val probe = CorpusRefreshProbe(ProjectRootModificationTracker.getInstance(project).modificationCount)
            val connection = project.messageBus.connect(parent)
            connection.subscribe(
                LuaSettingsChangedListener.TOPIC,
                object : LuaSettingsChangedListener {
                    override fun onSettingsChanged() {
                        probe.settingsBroadcasts++
                    }
                },
            )
            connection.subscribe(
                PsiManagerImpl.ANY_PSI_CHANGE_TOPIC,
                object : AnyPsiChangeListener {
                    override fun beforePsiChanged(isPhysical: Boolean) {
                        if (isPhysical) probe.physicalPsiChanges++
                    }
                },
            )
            return probe
        }
    }
}
