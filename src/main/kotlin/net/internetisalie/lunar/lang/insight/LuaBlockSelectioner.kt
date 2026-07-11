package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaBlock

/**
 * Adds a "body statements only" selection step for a [LuaBlock]: the span from the first
 * statement's start to the last statement's end, sitting between an individual statement and the
 * enclosing `function … end` / `do … end` / `if … end` shell (supplied by the platform default).
 */
class LuaBlockSelectioner : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement): Boolean = e is LuaBlock

    override fun select(
        e: PsiElement,
        editorText: CharSequence,
        cursorOffset: Int,
        editor: Editor,
    ): List<TextRange>? {
        val psiBlock = e as? LuaBlock ?: return null
        val statements = psiBlock.statementList
        val firstStatement = statements.firstOrNull() ?: return null
        val lastStatement = statements.last()
        return listOf(
            TextRange(firstStatement.textRange.startOffset, lastStatement.textRange.endOffset),
        )
    }
}
