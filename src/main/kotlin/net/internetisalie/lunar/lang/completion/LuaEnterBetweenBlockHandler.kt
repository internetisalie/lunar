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
import net.internetisalie.lunar.lang.psi.LuaBlockParent
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.syntax.LuaBlockPairs

/**
 * COMP-08-04 (design §3.4): when Enter is pressed with the caret between an already-matched opener
 * and its terminator, open a blank body line indented to the nested level and insert nothing.
 */
class LuaEnterBetweenBlockHandler : EnterHandlerDelegateAdapter() {
    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): EnterHandlerDelegate.Result {
        if (file !is LuaFile) return EnterHandlerDelegate.Result.Continue

        val offset = caretOffset.get()
        if (offset == 0) return EnterHandlerDelegate.Result.Continue

        PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)

        val leaf = file.findElementAt(offset - 1) ?: return EnterHandlerDelegate.Result.Continue
        val owner = PsiTreeUtil.getParentOfType(leaf, LuaBlockParent::class.java, false)
            ?: PsiTreeUtil.getParentOfType(leaf, LuaTableConstructor::class.java, false)
            ?: return EnterHandlerDelegate.Result.Continue

        val terminatorType = LuaBlockPairs.terminatorForOwner(owner)
        val terminator = owner.node.findChildByType(terminatorType) ?: return EnterHandlerDelegate.Result.Continue

        return if (offset in (leaf.textRange.endOffset + 1) until terminator.startOffset) {
            EnterHandlerDelegate.Result.DefaultForceIndent
        } else {
            EnterHandlerDelegate.Result.Continue
        }
    }
}
