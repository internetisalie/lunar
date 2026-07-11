package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaExprList
import net.internetisalie.lunar.lang.psi.LuaFieldList

/**
 * Adds the "all items, no brackets" Extend-Selection rung between a single list item and the
 * bracketed list: the span from the first item's start to the last item's end. Handles call/argument
 * expression lists ([LuaExprList]) and table constructors ([LuaFieldList]); the single-item and the
 * bracketed-list ranges come from the platform default. EDITOR-04-03. Stateless. Design §2.3 / §3.3.
 */
class LuaArgumentListSelectioner : ExtendWordSelectionHandlerBase() {

    override fun canSelect(e: PsiElement): Boolean = e is LuaExprList || e is LuaFieldList

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val items: List<PsiElement> = when (e) {
            is LuaExprList -> e.exprList
            is LuaFieldList -> e.fieldList
            else -> return null
        }
        val firstItem = items.firstOrNull() ?: return null
        val lastItem = items.last()
        return listOf(TextRange(firstItem.textRange.startOffset, lastItem.textRange.endOffset))
    }
}
