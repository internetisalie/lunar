package net.internetisalie.lunar.lang.smartenter

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
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
        addOpenerLeaf(element, result)
        var parent = element.parent
        while (parent != null && parent !is LuaFile) {
            result.add(parent)
            addOpenerLeaf(parent, result)
            parent = parent.parent
        }
    }

    /**
     * SYNTAX-18: a half-written skeleton now parses to a typed partial node (`LuaIfStatement` etc.),
     * and `getChildren()` on our composite PSI skips leaf tokens — so the base collector never feeds
     * the opener keyword leaf the token-keyed fixers match on. Add it explicitly (the opener is one
     * of the first two non-whitespace leaves: `local function` / `global function` offset it by one).
     */
    private fun addOpenerLeaf(element: PsiElement, result: MutableList<PsiElement>) {
        var child = element.firstChild
        var inspected = 0
        while (child != null && inspected < 2) {
            if (child !is PsiWhiteSpace) {
                if (child.firstChild == null && child.node?.elementType in LuaSmartEnterUtil.OPENERS) {
                    result.add(child)
                    return
                }
                inspected++
            }
            child = child.nextSibling
        }
    }
}
