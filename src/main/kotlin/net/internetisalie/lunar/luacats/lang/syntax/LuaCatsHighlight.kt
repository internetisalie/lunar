package net.internetisalie.lunar.luacats.lang.syntax

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import net.internetisalie.lunar.lang.syntax.LuaHighlight

object LuaCatsHighlight {
    val CONTENT: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS", DefaultLanguageHighlighterColors.DOC_COMMENT)
    var TAG: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS_TAG", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG)

    var KEYWORD: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS_KEYWORD", LuaHighlight.KEYWORD)
    var TYPE: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS_TYPE", LuaHighlight.PARAMETER)
    var NAME: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS_NAME", LuaHighlight.VAR_GLOBAL)
    var VALUE: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS_VALUE", LuaHighlight.STRING)
    var OPERATOR: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS_OPERATOR", LuaHighlight.OPERATORS)
}
