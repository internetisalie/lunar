package net.internetisalie.lunar.analysis.luacheck

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.LuaFileType

class LuaCheckAnnotator : ExternalAnnotator<LuaCheckAnnotator.Info, LuaCheckOutcome>() {

    class Info(
        val fileName: String,
        val workDir: VirtualFile,
        val documentText: String,
        val project: Project,
        val documentLineCount: Int,
        val lineStartOffsets: IntArray,
    )

    override fun getPairedBatchInspectionShortName(): String = LuaCheckInspection.SHORT_NAME

    override fun collectInformation(psiFile: PsiFile): Info? {
        val virtualFile = psiFile.virtualFile ?: return null
        if (!FileTypeRegistry.getInstance().isFileOfType(virtualFile, LuaFileType)) return null
        val workDir = virtualFile.parent ?: return null
        val document = psiFile.fileDocument
        val lineCount = maxOf(document.lineCount, 1)
        val lineStartOffsets = IntArray(lineCount) { document.getLineStartOffset(it) }
        return Info(
            fileName = psiFile.name,
            workDir = workDir,
            documentText = document.text,
            project = psiFile.project,
            documentLineCount = lineCount,
            lineStartOffsets = lineStartOffsets,
        )
    }

    override fun doAnnotate(collectedInfo: Info?): LuaCheckOutcome? {
        if (collectedInfo == null) return null
        return LuaCheckInvoker.invoke(collectedInfo)
    }

    override fun apply(file: PsiFile, annotationResult: LuaCheckOutcome, holder: AnnotationHolder) {
        val info = collectInformation(file) ?: return
        when (annotationResult) {
            is LuaCheckOutcome.Problems ->
                annotationResult.problems.distinctBy { it.lineStart to it.message }
                    .forEach { problem -> applyProblem(problem, info, holder) }
            is LuaCheckOutcome.Failure -> applyFailure(annotationResult, info, holder)
            LuaCheckOutcome.NotApplicable -> Unit
        }
    }

    private fun applyProblem(problem: Problem, info: Info, holder: AnnotationHolder) {
        val range = rangeFor(problem, info)
        holder.newAnnotation(HighlightSeverity.WARNING, problem.message ?: "Unspecified problem")
            .range(range)
            .create()
    }

    private fun rangeFor(problem: Problem, info: Info): TextRange {
        val length = info.documentText.length
        val line = problem.lineStart.coerceIn(0, info.documentLineCount - 1)
        val lineStart = info.lineStartOffsets[line]
        val lineEndExclusive =
            if (line + 1 < info.lineStartOffsets.size) info.lineStartOffsets[line + 1] else length
        val startOffset = (lineStart + problem.columnStart).coerceIn(lineStart, lineEndExclusive)
        val endOffset = (lineStart + problem.columnEnd + 1)
            .coerceIn(startOffset + 1, lineEndExclusive)
            .coerceAtMost(length)
        return TextRange(startOffset, maxOf(endOffset, startOffset))
    }

    private fun applyFailure(failure: LuaCheckOutcome.Failure, info: Info, holder: AnnotationHolder) {
        LOG.warn("luacheck failed (${failure.kind}): ${failure.detail}")
        val range = TextRange(0, info.documentText.length)
        holder.newAnnotation(HighlightSeverity.WARNING, failure.detail)
            .range(range)
            .create()
    }

    companion object {
        private val LOG = logger<LuaCheckAnnotator>()
    }
}
