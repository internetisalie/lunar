package net.internetisalie.lunar.lang.smartenter

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.syntax.LuaBlockPairs

/**
 * Completes a half-written block construct (EDITOR-08-01/-03): supplies the missing separator (`then`/`do`)
 * and terminator (`end`, or `until ` for `repeat`) for an opener keyword on the caret line, and drops the
 * caret into the (empty) body — or, for `repeat`, into the `until` condition. Idempotent: a construct that
 * already has its terminator is left alone. Fires off the opener **token** (the skeleton is an ERROR tree,
 * so PSI-class matching is unreliable). Design §3.2.
 */
class LuaBlockCompletionFixer : SmartEnterProcessorWithFixers.Fixer<LuaSmartEnterProcessor>() {

    override fun apply(editor: Editor, processor: LuaSmartEnterProcessor, element: PsiElement) {
        val opener = element.node?.elementType ?: return
        if (opener !in LuaSmartEnterUtil.OPENERS) return
        val document = editor.document
        val caretLine = document.getLineNumber(editor.caretModel.offset)
        if (document.getLineNumber(element.textRange.startOffset) != caretLine) return
        val terminator = LuaBlockPairs.terminatorByOpenerKeyword[opener] ?: return
        if (LuaSmartEnterUtil.alreadyTerminated(element, terminator)) return
        val lineEnd = document.getLineEndOffset(caretLine)
        // A param-less `function` needs its `()` first — defer to LuaFunctionParenFixer this pass.
        if (opener == LuaElementTypes.FUNCTION && !LuaSmartEnterUtil.hasTokenBefore(element, LuaElementTypes.LPAREN, lineEnd)) return

        val separator = LuaBlockPairs.separatorByOpenerKeyword[opener]
        val header = if (separator != null && !LuaSmartEnterUtil.hasTokenBefore(element, separator, lineEnd)) {
            " " + LuaSmartEnterUtil.separatorText(separator)
        } else {
            ""
        }
        val terminatorText = LuaSmartEnterUtil.terminatorText(terminator)
        if (terminator == LuaElementTypes.UNTIL) {
            val insert = "$header\n\n$terminatorText "
            document.insertString(lineEnd, insert)
            processor.registerUnresolvedError(lineEnd + insert.length) // caret after `until `
        } else {
            val bodyPrefix = "$header\n"
            document.insertString(lineEnd, "$bodyPrefix\n$terminatorText")
            processor.registerUnresolvedError(lineEnd + bodyPrefix.length) // caret on the empty body line
        }
    }
}
