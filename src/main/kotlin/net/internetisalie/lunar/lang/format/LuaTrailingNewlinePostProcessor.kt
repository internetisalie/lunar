package net.internetisalie.lunar.lang.format

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import net.internetisalie.lunar.lang.psi.LuaFile

/**
 * FORMAT-03-03: ensures a Lua file ends with exactly one trailing newline.
 *
 * Runs only for a whole-file reformat (range starting at offset 0 and reaching the trailing
 * whitespace), so partial reformats — a mid-file selection, or the range a postfix-template /
 * intention expansion reformats — never append or alter the trailing newline.
 */
class LuaTrailingNewlinePostProcessor : PostFormatProcessor {
    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (source !is LuaFile) return rangeToReformat

        val document = source.viewProvider.document ?: return rangeToReformat
        val text = document.charsSequence
        val length = text.length
        if (length == 0) return rangeToReformat

        // Only normalize EOF on a whole-file reformat: the range must start at the beginning and
        // reach the trailing whitespace. This excludes partial/selection reformats and the
        // narrow ranges template / intention expansions reformat.
        var contentEnd = length
        while (contentEnd > 0 && text[contentEnd - 1].isWhitespace()) contentEnd--
        if (rangeToReformat.startOffset != 0 || rangeToReformat.endOffset < contentEnd) return rangeToReformat

        val currentTail = text.subSequence(contentEnd, length).toString()
        if (currentTail == "\n") return rangeToReformat

        document.replaceString(contentEnd, length, "\n")
        PsiDocumentManager.getInstance(source.project).commitDocument(document)

        val delta = 1 - (length - contentEnd)
        val newEnd = (rangeToReformat.endOffset + delta).coerceIn(rangeToReformat.startOffset, document.textLength)
        return TextRange(rangeToReformat.startOffset, newEnd)
    }
}
