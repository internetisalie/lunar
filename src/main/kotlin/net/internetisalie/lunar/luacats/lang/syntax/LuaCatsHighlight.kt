package net.internetisalie.lunar.luacats.lang.syntax

import com.intellij.openapi.editor.colors.TextAttributesKey
import net.internetisalie.lunar.lang.syntax.LuaHighlight

object LuaCatsHighlight {
    val CONTENT: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS", LuaHighlight.DOC_COMMENT)
    var TAG: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS_TAG", LuaHighlight.DOC_TAG)

    var KEYWORD: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS_KEYWORD", LuaHighlight.KEYWORD)
    var TYPE: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS_TYPE", LuaHighlight.PARAMETER)
    var NAME: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS_NAME", LuaHighlight.VAR_GLOBAL)
    var VALUE: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS_VALUE", LuaHighlight.STRING)
    var SYMBOL: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUACATS_SYMBOL", LuaHighlight.OPERATORS)

}
