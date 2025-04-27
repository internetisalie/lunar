package net.internetisalie.lunar.lang.format

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDescription

class LuaEnterHandlerDelegate : EnterHandlerDelegate {
    var isLuaDoc : Boolean = false
    var isComment : Boolean = false

    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int?>,
        caretAdvance: Ref<Int?>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): EnterHandlerDelegate.Result? {
        if (file !is LuaFile) {
            return EnterHandlerDelegate.Result.Continue
        }

        isLuaDoc = false
        isComment = false

        val currentElement = file.findElementAt(editor.caretModel.offset) ?: return EnterHandlerDelegate.Result.Continue
        when {
            currentElement.parent is LuaCatsDescription -> isLuaDoc = true
            currentElement is PsiComment -> isComment = true
        }

        return EnterHandlerDelegate.Result.Continue
    }

    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext
    ): EnterHandlerDelegate.Result? {
        if (file !is LuaFile) {
            return EnterHandlerDelegate.Result.Continue
        }

        when {
            isLuaDoc -> {
                EditorModificationUtil.insertStringAtCaret(editor, "--- ")
                PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
            }
            isComment -> {
                EditorModificationUtil.insertStringAtCaret(editor, "-- ")
                PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
            }
        }

        return EnterHandlerDelegate.Result.Continue
    }
}