package net.internetisalie.lunar.luacats.lang.syntax

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

object LuaCatsHighlight {
    val LUADOC: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUADOC", DefaultLanguageHighlighterColors.DOC_COMMENT)
    var LUADOC_TAG: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUADOC_TAG", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG)
    var LUADOC_VALUE: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUADOC_VALUE", DefaultLanguageHighlighterColors.DOC_COMMENT)
}
