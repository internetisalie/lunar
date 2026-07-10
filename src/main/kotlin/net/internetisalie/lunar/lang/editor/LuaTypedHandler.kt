package net.internetisalie.lunar.lang.editor

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.syntax.LuaSyntax
import net.internetisalie.lunar.settings.LuaEditorOptions

/**
 * Typed-handler delegate for Lua files.
 *
 * Phase 1 — [beforeCharTyped]: suppresses the platform's bracket auto-close inside string and
 * comment context, realizing EDITOR-01-01 context-awareness (design §2.2, §3.1).
 *
 * Phase 2 — [charTyped]: keyword-block auto-closer via [LuaKeywordBlockCloser],
 * realizing EDITOR-01-05 keystroke path (design §3.5).
 */
class LuaTypedHandler : TypedHandlerDelegate() {

    private val bracketChars = setOf('(', '[', '{')

    /**
     * Vetoes bracket auto-close when the caret is inside a string or comment token (design §3.1).
     * Returns [Result.STOP] to prevent the platform from inserting the closing bracket.
     */
    override fun beforeCharTyped(
        c: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
        fileType: FileType,
    ): Result {
        if (file !is LuaFile || c !in bracketChars) return Result.CONTINUE
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val offset = editor.caretModel.offset
        if (offset == 0) return Result.CONTINUE
        val leaf = file.findElementAt(offset - 1) ?: return Result.CONTINUE
        val tokenType = leaf.node.elementType
        return if (LuaSyntax.CommentTokens.contains(tokenType) ||
            LuaSyntax.StringLiteralTokens.contains(tokenType)
        ) {
            Result.STOP
        } else {
            Result.CONTINUE
        }
    }

    /**
     * Keyword-block auto-closer: when a space is typed after a block-opener keyword (do, then,
     * function, repeat), scaffolds the matching terminator via [LuaKeywordBlockCloser].
     * Gated on [LuaEditorOptions.autoCloseKeywordBlocks]. Design §2.2, §3.5.
     */
    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is LuaFile || c != ' ') return Result.CONTINUE
        if (!LuaEditorOptions.instance.autoCloseKeywordBlocks) return Result.CONTINUE
        return triggerKeywordBlockCloser(project, editor, file)
    }

    private fun triggerKeywordBlockCloser(project: Project, editor: Editor, file: PsiFile): Result {
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val offset = editor.caretModel.offset
        if (offset < 2) return Result.CONTINUE
        val leaf = file.findElementAt(offset - 2) ?: return Result.CONTINUE
        LuaKeywordBlockCloser.closeIfNeeded(editor, file, leaf.textRange.endOffset)
        return Result.CONTINUE
    }
}
