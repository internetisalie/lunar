package net.internetisalie.lunar.lang.syntax

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

object LuaHighlight {
    var LOCAL_VAR: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_LOCAL_VAR", DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
    val UPVAL: TextAttributesKey = TextAttributesKey.createTextAttributesKey("UPVAL", LOCAL_VAR)
    val PARAMETER: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER)
    val GLOBAL_VAR: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUA_GLOBAL_VAR",
        DefaultLanguageHighlighterColors.GLOBAL_VARIABLE
    )
    val FIELD: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_FIELD", DefaultLanguageHighlighterColors.STATIC_FIELD)

    val TAIL_CALL: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_TAIL_CALL", HighlighterColors.TEXT)

    val KEYWORD: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)

    val COMMENT: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val LONGCOMMENT: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_LONGCOMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
    val LONGCOMMENT_BRACES: TextAttributesKey = TextAttributesKey
        .createTextAttributesKey("LUA_LONGCOMMENT_BRACES", DefaultLanguageHighlighterColors.BLOCK_COMMENT)

    val NUMBER: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_NUMBER", DefaultLanguageHighlighterColors.NUMBER)

    val STRING: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_STRING", DefaultLanguageHighlighterColors.STRING)
    val LONGSTRING: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_LONGSTRING", DefaultLanguageHighlighterColors.STRING)
    val LONGSTRING_BRACES: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_LONGSTRING_BRACES", DefaultLanguageHighlighterColors.STRING)

    val BRACKETS: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
    val BRACES: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_BRACES", DefaultLanguageHighlighterColors.BRACES)
    val PARENTHESES: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_PARENTHS", DefaultLanguageHighlighterColors.PARENTHESES)
    val BAD_CHARACTER: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
    val OPERATORS: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_OPERATORS", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val COMMA: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_COMMA", DefaultLanguageHighlighterColors.COMMA)

    val SEMI: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("LUA_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)

    val DEFINED_CONSTANTS: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "LUA_DEFINED_CONSTANTS",
        DefaultLanguageHighlighterColors.CONSTANT
    )
}