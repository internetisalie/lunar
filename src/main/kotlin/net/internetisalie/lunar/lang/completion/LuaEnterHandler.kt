package net.internetisalie.lunar.lang.completion

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaElementTypes

class LuaEnterHandler : EnterHandlerDelegateAdapter() {
    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): EnterHandlerDelegate.Result {
        if (file !is LuaFile) {
            return EnterHandlerDelegate.Result.Continue
        }

        val offset = caretOffset.get()
        if (offset == 0) return EnterHandlerDelegate.Result.Continue

        val document = editor.document
        PsiDocumentManager.getInstance(file.project).commitDocument(document)

        val element = file.findElementAt(offset - 1) ?: return EnterHandlerDelegate.Result.Continue
        val elementType = element.node.elementType

        if (elementType == LuaElementTypes.THEN || elementType == LuaElementTypes.DO || elementType == LuaElementTypes.FUNCTION || elementType == LuaElementTypes.REPEAT) {
            val block = PsiTreeUtil.getParentOfType(element, LuaBlock::class.java)
            // Ideally we should check if `end` is already matching this token, but as a basic auto-completion
            // we insert it if there's nothing immediately after
            
            // Just insert "end" (or "until" for REPEAT) in the next line
            val keyword = if (elementType == LuaElementTypes.REPEAT) "until" else "end"
            
            document.insertString(offset, "\n$keyword")
            editor.caretModel.moveToOffset(offset)
            return EnterHandlerDelegate.Result.DefaultForceIndent
        }

        return EnterHandlerDelegate.Result.Continue
    }
}
