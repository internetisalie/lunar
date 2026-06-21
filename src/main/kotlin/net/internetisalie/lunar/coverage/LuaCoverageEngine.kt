package net.internetisalie.lunar.coverage

import com.intellij.coverage.CoverageAnnotator
import com.intellij.coverage.CoverageEngine
import com.intellij.coverage.CoverageFileProvider
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.CoverageSuite
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.BaseCoverageSuite
import com.intellij.coverage.DefaultCoverageFileProvider
import com.intellij.coverage.view.CoverageViewExtension
import com.intellij.coverage.view.CoverageViewManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.run.test.LuaTestRunConfiguration
import java.io.File

class LuaCoverageSuite : BaseCoverageSuite {
    constructor() : super()
    constructor(
        name: String,
        project: Project?,
        runner: CoverageRunner?,
        fileProvider: CoverageFileProvider?,
        timestamp: Long
    ) : super(name, project, runner, fileProvider, timestamp)

    override fun getCoverageEngine(): CoverageEngine =
        CoverageEngine.EP_NAME.findExtension(LuaCoverageEngine::class.java) ?: LuaCoverageEngine()
}

class LuaCoverageEnabledConfiguration(
    configuration: RunConfigurationBase<*>
) : CoverageEnabledConfiguration(
    configuration,
    CoverageRunner.getInstanceById("LuaCov") ?: LuaCoverageRunner()
)

class LuaCoverageEngine : CoverageEngine() {
    override fun getPresentableText(): String = "Lua Coverage"

    override fun isApplicableTo(conf: RunConfigurationBase<*>): Boolean =
        conf is LuaTestRunConfiguration

    override fun createCoverageEnabledConfiguration(conf: RunConfigurationBase<*>): CoverageEnabledConfiguration =
        LuaCoverageEnabledConfiguration(conf)

    override fun createCoverageSuite(
        name: String,
        project: Project,
        runner: CoverageRunner,
        fileProvider: CoverageFileProvider,
        timestamp: Long
    ): CoverageSuite = LuaCoverageSuite(name, project, runner, fileProvider, timestamp)

    override fun createEmptyCoverageSuite(coverageRunner: CoverageRunner): CoverageSuite? =
        LuaCoverageSuite(
            "",
            null,
            coverageRunner,
            DefaultCoverageFileProvider(File("")),
            0
        )

    override fun getCoverageAnnotator(project: Project): CoverageAnnotator =
        LuaCoverageAnnotator.getInstance(project)

    override fun coverageEditorHighlightingApplicableTo(file: PsiFile): Boolean =
        file.fileType == LuaFileType

    override fun getQualifiedNames(sourceFile: PsiFile): Set<String> =
        setOf(sourceFile.virtualFile.path)

    override fun getQualifiedName(outputFile: File, sourceFile: PsiFile): String =
        outputFile.path

    override fun coverageProjectViewStatisticsApplicableTo(fileOrDir: VirtualFile): Boolean =
        !fileOrDir.isDirectory && fileOrDir.fileType == LuaFileType

    override fun acceptedByFilters(psiFile: PsiFile, bundle: CoverageSuitesBundle): Boolean =
        psiFile.fileType == LuaFileType
}
