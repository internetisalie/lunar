package net.internetisalie.lunar.coverage.report

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors

object LuaCovReportHighlight {
    val HEADER: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUACOV_HEADER", DefaultLanguageHighlighterColors.METADATA
    )
    val FILE_PATH: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUACOV_FILE_PATH", DefaultLanguageHighlighterColors.STRING
    )
    val COVERED: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUACOV_COVERED", DefaultLanguageHighlighterColors.STRING
    )
    val UNCOVERED: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUACOV_UNCOVERED", DefaultLanguageHighlighterColors.KEYWORD
    )
}
