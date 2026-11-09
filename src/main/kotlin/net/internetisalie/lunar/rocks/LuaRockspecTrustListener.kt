package net.internetisalie.lunar.rocks

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

/**
 * Re-runs rockspec discovery when a project becomes trusted: until then [RockspecBridge.read]
 * evaluates nothing (BUG-50), so every cached result is empty.
 */
class LuaRockspecTrustListener : TrustedProjectsListener {
    override fun onProjectTrusted(project: Project) {
        if (project.isDisposed) return
        RockspecSourcePathProvider.refresh(project)
        ApplicationManager.getApplication().invokeLater({
            PsiManager.getInstance(project).dropPsiCaches()
            DaemonCodeAnalyzer.getInstance(project).restart()
        }, project.disposed)
    }
}
