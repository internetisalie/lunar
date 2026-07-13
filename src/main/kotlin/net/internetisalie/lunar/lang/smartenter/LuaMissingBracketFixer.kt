package net.internetisalie.lunar.lang.smartenter

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace

/**
 * Balances unclosed `(`/`{`/`[` on the caret line (EDITOR-08-02): `print("x"` → `print("x")`,
 * `local t = { 1, 2` → `local t = { 1, 2 }`. A LIFO stack over the line's leaf tokens (string/comment
 * leaves are single tokens, naturally skipped); on a non-matching closer it bails rather than guessing.
 * Runs once, gated on the caret leaf. Design §3.2.
 */
class LuaMissingBracketFixer : SmartEnterProcessorWithFixers.Fixer<LuaSmartEnterProcessor>() {

    override fun apply(editor: Editor, processor: LuaSmartEnterProcessor, element: PsiElement) {
        val document = editor.document
        val caret = editor.caretModel.offset
        val file = element.containingFile ?: return
        if (element != file.findElementAt(caret - 1)) return // run exactly once, off the caret leaf
        val caretLine = document.getLineNumber(caret)
        val lineStart = document.getLineStartOffset(caretLine)
        val lineEnd = document.getLineEndOffset(caretLine)
        val closers = LuaSmartEnterUtil.unbalancedClosers(lineTokens(file, lineStart, lineEnd))
        if (closers.isEmpty()) return
        val text = closers.joinToString("")
        document.insertString(lineEnd, text)
        processor.registerUnresolvedError(lineEnd + text.length)
    }

    private fun lineTokens(file: PsiFile, lineStart: Int, lineEnd: Int): List<PsiElement> {
        val tokens = mutableListOf<PsiElement>()
        var offset = lineStart
        while (offset < lineEnd) {
            val leaf = file.findElementAt(offset) ?: break
            if (leaf !is PsiWhiteSpace) tokens.add(leaf)
            offset = maxOf(leaf.textRange.endOffset, offset + 1)
        }
        return tokens
    }
}
