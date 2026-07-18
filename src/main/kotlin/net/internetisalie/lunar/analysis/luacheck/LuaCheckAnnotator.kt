package net.internetisalie.lunar.analysis.luacheck

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.LuaFileType

class LuaCheckAnnotator : ExternalAnnotator<LuaCheckAnnotator.Info, LuaCheckOutcome>() {
    class Info(
        val virtualFile: VirtualFile,
        val psiFile: PsiFile,
    )

    override fun getPairedBatchInspectionShortName(): String = LuaCheckInspection.SHORT_NAME

    override fun collectInformation(psiFile: PsiFile): Info? {
        val virtualFile = psiFile.virtualFile ?: return null
        if (!FileTypeRegistry.getInstance().isFileOfType(virtualFile, LuaFileType)) return null
        return Info(virtualFile, psiFile)
    }

    override fun doAnnotate(collectedInfo: Info?): LuaCheckOutcome? {
        if (collectedInfo == null) return null
        return LuaCheckInvoker.invoke(collectedInfo.virtualFile, collectedInfo.psiFile)
    }

    override fun apply(file: PsiFile, annotationResult: LuaCheckOutcome, holder: AnnotationHolder) {
        if (annotationResult !is LuaCheckOutcome.Problems) return
        annotationResult.problems.distinctBy { it.lineStart to it.message }
            .forEach { problem -> applyProblem(file, problem, holder) }
    }

    private fun applyProblem(file: PsiFile, problem: Problem, holder: AnnotationHolder) {
        val startOffset = file.fileDocument.getLineStartOffset(problem.lineStart) + problem.columnStart
        val endOffset = file.fileDocument.getLineStartOffset(problem.lineEnd) + problem.columnEnd + 1
        val range = TextRange(startOffset, endOffset)

        holder.newAnnotation(HighlightSeverity.WARNING, problem.message ?: "Unspecified problem")
            .range(range)
            .create()
    }
}
