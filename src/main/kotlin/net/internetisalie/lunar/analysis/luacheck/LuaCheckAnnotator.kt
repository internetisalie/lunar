package net.internetisalie.lunar.analysis.luacheck

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.LuaFileType

class LuaCheckAnnotator : ExternalAnnotator<LuaCheckAnnotator.Info, LuaCheckAnnotator.Results>() {
    class Info(
        val virtualFile: VirtualFile,
        val psiFile: PsiFile,
    )

    class Results(
        val problems: List<Problem>
    )

    override fun getPairedBatchInspectionShortName(): String = LuaCheckInspection.SHORT_NAME

    override fun collectInformation(psiFile: PsiFile): Info? {
        val virtualFile = psiFile.virtualFile ?: return null
        if (!FileTypeRegistry.getInstance().isFileOfType(virtualFile, LuaFileType)) return null
        return Info(virtualFile, psiFile)
    }

    override fun doAnnotate(collectedInfo: Info?): Results? {
        if (collectedInfo == null) return null
        val problems = LuaCheckInvoker.invoke(collectedInfo.virtualFile, collectedInfo.psiFile)
        val deduplicated = problems.distinctBy { it.lineStart to it.message }
        return Results(deduplicated)
    }

    override fun apply(file: PsiFile, annotationResult: Results, holder: AnnotationHolder) {
        val uniqueProblems = annotationResult.problems.distinctBy { it.lineStart to it.message }
        uniqueProblems.forEach { problem -> applyProblem(file, problem, holder) }
    }

    private fun applyProblem(file: PsiFile, problem: Problem, holder: AnnotationHolder) {
        val startOffset = file.fileDocument.getLineStartOffset(problem.lineStart) + problem.columnStart
        val endOffset = file.fileDocument.getLineStartOffset(problem.lineEnd) + problem.columnEnd + 1
        val range = TextRange(startOffset, endOffset)

        val builder = holder.newAnnotation(HighlightSeverity.WARNING, problem.message ?: "Unspecified problem")
        builder.range(range)

        if (problem.name != null) {
            builder.tooltip(problem.name!!)
        }

        builder.create()
    }
}
