package net.internetisalie.lunar.coverage

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.SimpleCoverageAnnotator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile

class LuaCoverageAnnotator(project: Project) : SimpleCoverageAnnotator(project) {

    companion object {
        fun getInstance(project: Project): LuaCoverageAnnotator =
            project.getService(LuaCoverageAnnotator::class.java)
    }

    override fun getFileCoverageInformationString(
        file: PsiFile,
        bundle: CoverageSuitesBundle,
        manager: CoverageDataManager
    ): String? = super.getFileCoverageInformationString(file, bundle, manager)

    override fun getDirCoverageInformationString(
        dir: PsiDirectory,
        bundle: CoverageSuitesBundle,
        manager: CoverageDataManager
    ): String? = super.getDirCoverageInformationString(dir, bundle, manager)
}
