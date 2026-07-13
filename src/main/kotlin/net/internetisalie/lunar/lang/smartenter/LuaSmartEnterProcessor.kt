package net.internetisalie.lunar.lang.smartenter

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaFile

/**
 * Smart Enter (Ctrl+Shift+Enter / `EditorCompleteStatement`) for Lua: completes a half-written block or
 * call and drops the caret at the next edit point (EDITOR-08). [getStatementAtCaret] returns the common
 * parent of the caret line's first leaf and the caret leaf, so the opener keyword token is always inside
 * the collected element queue even though the skeleton parses to an ERROR tree; [collectAdditionalElements]
 * adds ancestors too. Caret placement rides `registerUnresolvedError`, which the base `doEnter` honors.
 * Design §3.1.
 */
class LuaSmartEnterProcessor : SmartEnterProcessorWithFixers() {

    init {
        addFixers(
            LuaMissingBracketFixer(),
            LuaFunctionParenFixer(),
            LuaBlockCompletionFixer(),
        )
    }

    override fun getStatementAtCaret(editor: Editor, psiFile: PsiFile): PsiElement? {
        if (psiFile !is LuaFile) return null
        val document = editor.document
        val caret = editor.caretModel.offset
        val endLeaf = psiFile.findElementAt(caret - 1) ?: psiFile.findElementAt(caret) ?: return null
        val lineStart = document.getLineStartOffset(document.getLineNumber(caret))
        val startLeaf = LuaSmartEnterUtil.firstLeafFrom(endLeaf, lineStart, caret) ?: endLeaf
        return PsiTreeUtil.findCommonParent(startLeaf, endLeaf) ?: endLeaf
    }

    override fun collectAdditionalElements(element: PsiElement, result: MutableList<PsiElement>) {
        var parent = element.parent
        while (parent != null && parent !is LuaFile) {
            result.add(parent)
            parent = parent.parent
        }
    }
}
