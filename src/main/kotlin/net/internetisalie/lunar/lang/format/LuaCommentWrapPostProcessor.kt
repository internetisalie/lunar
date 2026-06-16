package net.internetisalie.lunar.lang.format

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile

/**
 * FORMAT-06-02: hard-wraps over-long `--` line comments at the right margin when
 * `WRAP_LONG_COMMENTS` is enabled. LuaCATS doc comments (`---@…`, a distinct element type) are
 * never touched, and an over-long single token (e.g. a URL) is left on its own line.
 */
class LuaCommentWrapPostProcessor : PostFormatProcessor {
    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (source !is LuaFile) return rangeToReformat
        val luaSettings = LuaCodeStyleSettings.getInstance(settings) ?: return rangeToReformat
        if (!luaSettings.WRAP_LONG_COMMENTS) return rangeToReformat

        val rightMargin = settings.getRightMargin(LuaLanguage)
        if (rightMargin <= 0) return rangeToReformat

        val document = source.viewProvider.document ?: return rangeToReformat

        val comments = mutableListOf<PsiElement>()
        PsiTreeUtil.processElements(source) { element ->
            if (element.node?.elementType == LuaElementTypes.SHORTCOMMENT &&
                rangeToReformat.contains(element.textRange.startOffset)
            ) {
                comments.add(element)
            }
            true
        }
        if (comments.isEmpty()) return rangeToReformat

        var totalDelta = 0
        // Process tail-first so earlier offsets remain valid as we edit.
        for (comment in comments.sortedByDescending { it.textRange.startOffset }) {
            val startOffset = comment.textRange.startOffset
            val endOffset = comment.textRange.endOffset
            val lineStart = document.getLineStartOffset(document.getLineNumber(startOffset))
            val column = startOffset - lineStart
            val commentText = comment.text
            if (column + commentText.length <= rightMargin) continue

            val wrapped = wrapComment(commentText, column, rightMargin) ?: continue
            document.replaceString(startOffset, endOffset, wrapped)
            totalDelta += wrapped.length - commentText.length
        }

        if (totalDelta != 0) {
            PsiDocumentManager.getInstance(source.project).commitDocument(document)
        }
        val newEnd = (rangeToReformat.endOffset + totalDelta)
            .coerceIn(rangeToReformat.startOffset, document.textLength)
        return TextRange(rangeToReformat.startOffset, newEnd)
    }

    /**
     * Greedily packs the comment body into `<indent><dashes> <words>` lines no wider than the
     * right margin. Returns null when nothing needs wrapping (≤1 resulting line).
     */
    private fun wrapComment(text: String, column: Int, rightMargin: Int): String? {
        val dashes = text.takeWhile { it == '-' }
        val body = text.substring(dashes.length).trim()
        if (body.isEmpty()) return null

        val words = body.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null

        val indent = " ".repeat(column)
        val prefix = "$dashes "
        val budget = (rightMargin - (column + prefix.length)).coerceAtLeast(1)

        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            when {
                current.isEmpty() -> current.append(word)
                current.length + 1 + word.length <= budget -> current.append(' ').append(word)
                else -> {
                    lines.add(current.toString())
                    current = StringBuilder(word)
                }
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        if (lines.size <= 1) return null

        return lines.joinToString("\n$indent") { "$prefix$it" }
    }
}
