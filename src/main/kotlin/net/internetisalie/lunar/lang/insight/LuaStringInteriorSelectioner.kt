package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.syntax.getLuaStringDelimiterLength

/**
 * Inside a Lua `STRING` literal, offers two Extend-Selection steps: the content without the
 * quote / long-bracket delimiters, then the full literal. Delegates delimiter measurement to the
 * hardened [getLuaStringDelimiterLength] (0 on degenerate input), returning null for unterminated
 * strings so the platform default applies. EDITOR-04-02. Stateless. Design §2.1 / §3.1.
 */
class LuaStringInteriorSelectioner : ExtendWordSelectionHandlerBase() {

    override fun canSelect(e: PsiElement): Boolean = e.node?.elementType == LuaElementTypes.STRING

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val raw = e.text
        val delimiterLength = getLuaStringDelimiterLength(raw)
        if (delimiterLength == 0 || raw.length < 2 * delimiterLength) return null
        val contentStart = e.textRange.startOffset + delimiterLength
        val contentEnd = maxOf(contentStart, e.textRange.endOffset - delimiterLength)
        return listOf(TextRange(contentStart, contentEnd), e.textRange)
    }
}
