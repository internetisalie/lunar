package net.internetisalie.lunar.coverage.report

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class LuaCovReportSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = LuaCovReportLexer()
    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            LuaCovReportLexer.HEADER_BOUNDARY -> pack(LuaCovReportHighlight.HEADER)
            LuaCovReportLexer.FILE_PATH -> pack(LuaCovReportHighlight.FILE_PATH)
            LuaCovReportLexer.HIT_COVERED -> pack(LuaCovReportHighlight.COVERED)
            LuaCovReportLexer.HIT_UNCOVERED -> pack(LuaCovReportHighlight.UNCOVERED)
            else -> TextAttributesKey.EMPTY_ARRAY
        }
    }
}
