package net.internetisalie.lunar.lang.smartenter

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaElementTypes

/**
 * Supplies the empty parameter parens for a param-less `function foo` (EDITOR-08-01). Only acts when
 * **neither** paren token is present on the header line; the open-but-unclosed case (`function foo(`) is
 * owned by [LuaMissingBracketFixer]. Fires off the `FUNCTION` opener token. Design §3.2.
 */
class LuaFunctionParenFixer : SmartEnterProcessorWithFixers.Fixer<LuaSmartEnterProcessor>() {

    override fun apply(editor: Editor, processor: LuaSmartEnterProcessor, element: PsiElement) {
        if (element.node?.elementType != LuaElementTypes.FUNCTION) return
        val document = editor.document
        val caretLine = document.getLineNumber(editor.caretModel.offset)
        if (document.getLineNumber(element.textRange.startOffset) != caretLine) return
        val lineEnd = document.getLineEndOffset(caretLine)
        if (LuaSmartEnterUtil.hasTokenBefore(element, LuaElementTypes.LPAREN, lineEnd)) return
        document.insertString(lineEnd, "()")
        processor.registerUnresolvedError(lineEnd + 1) // caret between the parens
    }
}
