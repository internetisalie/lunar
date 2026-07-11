package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaElementTypes

/**
 * Inside a `SHORTCOMMENT` (`--`) or `LONGCOMMENT` (`--[==[ … ]==]`) leaf, offers the comment text
 * without its markers, then the full comment. `LUACATS_COMMENT` (`---@…`) is intentionally excluded
 * (its tag PSI is out of scope). Returns null on a marker-only/degenerate comment so the platform
 * default applies, and never throws. EDITOR-04-04. Stateless. Design §2.2 / §3.2.
 */
class LuaCommentInteriorSelectioner : ExtendWordSelectionHandlerBase() {

    override fun canSelect(e: PsiElement): Boolean {
        val type = e.node?.elementType
        return type == LuaElementTypes.SHORTCOMMENT || type == LuaElementTypes.LONGCOMMENT
    }

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val interior = if (e.node?.elementType == LuaElementTypes.SHORTCOMMENT) {
            shortCommentInterior(e.text, e.textRange)
        } else {
            longCommentInterior(e.text, e.textRange)
        }
        return interior?.let { listOf(it, e.textRange) }
    }

    /** `--` prefix plus any immediately-following spaces/tabs stripped. */
    private fun shortCommentInterior(raw: String, range: TextRange): TextRange? {
        if (!raw.startsWith("--")) return null
        var prefix = 2
        while (prefix < raw.length && (raw[prefix] == ' ' || raw[prefix] == '\t')) prefix++
        val textStart = range.startOffset + prefix
        return if (textStart >= range.endOffset) null else TextRange(textStart, range.endOffset)
    }

    /** `--[` + level `=` + `[` opening and `]` + level `=` + `]` closing markers stripped. */
    private fun longCommentInterior(raw: String, range: TextRange): TextRange? {
        var level = 0
        while (level + 3 < raw.length && raw[level + 3] == '=') level++
        val textStart = range.startOffset + level + 4
        val textEnd = range.endOffset - (level + 2)
        return if (textStart >= textEnd) null else TextRange(textStart, textEnd)
    }
}
