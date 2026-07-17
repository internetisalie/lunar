package net.internetisalie.lunar.lang.format

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Ref
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.insight.LuaDocGenerator
import net.internetisalie.lunar.lang.psi.*

class LuaEnterHandlerDelegate : EnterHandlerDelegate {

    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int?>,
        caretAdvance: Ref<Int?>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): Result = Result.Continue

    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext
    ): Result {
        if (file !is LuaFile) {
            return Result.Continue
        }

        val document = editor.document
        val caretOffset = editor.caretModel.offset
        val line = document.getLineNumber(caretOffset)

        // Don't process if we're on the first line
        if (line == 0) {
            return Result.Continue
        }

        // Check the previous line to see if it's a LuaDOC comment
        val prevLine = line - 1
        val prevLineStart = document.getLineStartOffset(prevLine)
        val prevLineEnd = document.getLineEndOffset(prevLine)
        val prevLineText = document.getText(com.intellij.openapi.util.TextRange(prevLineStart, prevLineEnd))
        val prevLineIndent = getLineIndent(document, prevLineStart)
        val prevLineTrimmed = prevLineText.trim()

        // Check if previous line is a LuaDOC comment (starts with ---)
        if (!prevLineTrimmed.startsWith("---")) {
            return Result.Continue
        }

        val currentLineStart = document.getLineStartOffset(line)
        val currentLineEnd = document.getLineEndOffset(line)
        val currentLineText = document.getText(com.intellij.openapi.util.TextRange(currentLineStart, currentLineEnd))

        // If current line already has content or starts with ---, don't modify it
        // (this happens when Enter was pressed mid-line)
        if (currentLineText.isNotEmpty()) {
            return Result.Continue
        }

        // DOC-04: If previous line was exactly "---", try to generate boilerplate
        if (prevLineTrimmed == "---") {
            var leaf = file.findElementAt(currentLineEnd)
            while (leaf != null && (leaf is com.intellij.psi.PsiWhiteSpace || leaf is com.intellij.psi.PsiComment)) {
                leaf = PsiTreeUtil.nextLeaf(leaf)
            }

            val target = PsiTreeUtil.getNonStrictParentOfType(leaf, LuaCommentOwner::class.java)
            if (target != null && LuaDocGenerator.isDocCommentEmpty(target.catsComment)) {
                val template = LuaDocGenerator.createTemplate(file.project, target, prevLineIndent)
                if (template != null) {
                    val endOffset = if (line < document.lineCount - 1) document.getLineStartOffset(line + 1) else document.textLength
                    document.deleteString(prevLineStart, endOffset)
                    editor.caretModel.moveToOffset(prevLineStart)
                    TemplateManager.getInstance(file.project).startTemplate(editor, template)
                    return Result.Stop
                }
            }
        }

        // Auto-continue: Insert the prefix "--- " on the new line
        document.insertString(currentLineStart, "$prevLineIndent--- ")

        // Move caret to after the prefix
        editor.caretModel.moveToOffset(currentLineStart + prevLineIndent.length + 4)

        return Result.Continue
    }

    private fun getLineIndent(document: Document, lineStart: Int): String {
        val text = document.charsSequence
        var i = lineStart
        while (i < text.length && text[i].isWhitespace()) {
            i++
        }
        return text.subSequence(lineStart, i).toString()
    }
}
