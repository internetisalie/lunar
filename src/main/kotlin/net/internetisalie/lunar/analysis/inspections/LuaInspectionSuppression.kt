package net.internetisalie.lunar.analysis.inspections

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.syntax.LuaSyntax

/**
 * Decides whether a flagged name at a source offset is suppressed by a nearby
 * `---@diagnostic` or `-- luacheck: ignore` comment.
 *
 * The suppression model for a file is parsed once and cached per [PsiFile] (invalidated on
 * any PSI change) via [CachedValuesManager].
 */
object LuaInspectionSuppression {

    /** A single comment-derived suppression rule resolved to absolute source line numbers. */
    private data class SuppressionRange(
        val startLine: Int,
        val endLine: Int,
        val names: Set<String>,
        val allDiagnostics: Boolean,
    ) {
        fun covers(line: Int, name: String, diagnosticId: String): Boolean {
            if (line < startLine || line > endLine) return false
            if (allDiagnostics) return true
            return diagnosticId in names || name in names
        }
    }

    private val DIAGNOSTIC_REGEX =
        Regex("""^---@diagnostic\s+(disable-next-line|disable-line|disable|enable)\s*:\s*([\w,\s-]+)$""")
    private val DIAGNOSTIC_BARE_REGEX =
        Regex("""^---@diagnostic\s+(disable-next-line|disable-line|disable|enable)\s*$""")
    private val LUACHECK_REGEX = Regex("""--\s*luacheck:\s*ignore\b(.*)$""")

    fun isSuppressed(ref: LuaNameRef, name: String, diagnosticId: String): Boolean {
        val file = ref.containingFile ?: return false
        val line = lineOf(file, ref.textOffset) ?: return false
        val ranges = suppressionRangesFor(file)
        return ranges.any { it.covers(line, name, diagnosticId) }
    }

    private fun suppressionRangesFor(file: PsiFile): List<SuppressionRange> {
        return CachedValuesManager.getManager(file.project).getCachedValue(file) {
            CachedValueProvider.Result.create(parseFile(file), PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    private fun parseFile(file: PsiFile): List<SuppressionRange> {
        val comments = PsiTreeUtil.collectElements(file) { it.elementType in LuaSyntax.CommentTokens }
        val lineCount = lineCount(file)
        val open = mutableListOf<OpenDisableBlock>()
        val ranges = mutableListOf<SuppressionRange>()
        for (comment in comments) {
            val commentLine = lineOf(file, comment.textOffset) ?: continue
            parseDiagnostic(comment, commentLine, lineCount, open, ranges)
            parseLuacheck(comment, commentLine, lineCount, ranges)
        }
        open.forEach { ranges.add(it.close(lineCount)) }
        return ranges
    }

    private data class OpenDisableBlock(val startLine: Int, val names: Set<String>, val allDiagnostics: Boolean) {
        fun close(endLine: Int) = SuppressionRange(startLine, endLine, names, allDiagnostics)
    }

    private fun parseDiagnostic(
        comment: PsiElement,
        commentLine: Int,
        lineCount: Int,
        open: MutableList<OpenDisableBlock>,
        ranges: MutableList<SuppressionRange>,
    ) {
        val text = comment.text.trim()
        val bare = DIAGNOSTIC_BARE_REGEX.matchEntire(text)
        val match = DIAGNOSTIC_REGEX.matchEntire(text)
        val keyword = bare?.groupValues?.get(1) ?: match?.groupValues?.get(1) ?: return
        val names = match?.let { splitNames(it.groupValues[2]) } ?: emptySet()
        val allDiagnostics = bare != null
        if (!allDiagnostics && DIAGNOSTIC_ID !in names) {
            if (keyword == "enable") closeBlocks(open, ranges, commentLine, names)
            return
        }
        applyDiagnostic(keyword, commentLine, lineCount, names, allDiagnostics, open, ranges)
    }

    private fun applyDiagnostic(
        keyword: String,
        commentLine: Int,
        lineCount: Int,
        names: Set<String>,
        allDiagnostics: Boolean,
        open: MutableList<OpenDisableBlock>,
        ranges: MutableList<SuppressionRange>,
    ) {
        when (keyword) {
            "disable-line" -> ranges.add(SuppressionRange(commentLine, commentLine, names, allDiagnostics))
            "disable-next-line" ->
                ranges.add(SuppressionRange(commentLine + 1, minOf(commentLine + 1, lineCount), names, allDiagnostics))
            "disable" -> open.add(OpenDisableBlock(commentLine, names, allDiagnostics))
            "enable" -> closeBlocks(open, ranges, commentLine, names)
        }
    }

    private fun closeBlocks(
        open: MutableList<OpenDisableBlock>,
        ranges: MutableList<SuppressionRange>,
        enableLine: Int,
        names: Set<String>,
    ) {
        val iterator = open.iterator()
        while (iterator.hasNext()) {
            val block = iterator.next()
            ranges.add(SuppressionRange(block.startLine, enableLine - 1, block.names, block.allDiagnostics))
            iterator.remove()
        }
    }

    private fun parseLuacheck(
        comment: PsiElement,
        commentLine: Int,
        lineCount: Int,
        ranges: MutableList<SuppressionRange>,
    ) {
        val match = LUACHECK_REGEX.find(comment.text) ?: return
        val names = splitNames(match.groupValues[1])
        val allDiagnostics = names.isEmpty()
        ranges.add(SuppressionRange(commentLine, minOf(commentLine + 1, lineCount), names, allDiagnostics))
    }

    private fun splitNames(raw: String): Set<String> =
        raw.split(Regex("""[,\s]+""")).map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun lineOf(file: PsiFile, offset: Int): Int? =
        PsiDocumentManager.getInstance(file.project).getDocument(file)?.getLineNumber(offset)

    private fun lineCount(file: PsiFile): Int =
        (PsiDocumentManager.getInstance(file.project).getDocument(file)?.lineCount ?: 1) - 1

    private const val DIAGNOSTIC_ID = "undefined-global"
}
