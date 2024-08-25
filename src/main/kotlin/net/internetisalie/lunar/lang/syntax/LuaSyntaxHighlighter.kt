/*
 * Copyright 2009 Max Ishchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.internetisalie.lunar.lang.syntax

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.lexer.LuaLexer
import net.internetisalie.lunar.lang.lexer.LuaTokenTypes
import net.internetisalie.lunar.lang.psi.LuaElementTypes

/**
 * Created by IntelliJ IDEA.
 * User: Max
 * Date: 06.07.2009
 * Time: 16:40:05
 */
class LuaSyntaxHighlighter : SyntaxHighlighterBase() {
    private val colors: MutableMap<IElementType, TextAttributesKey> = HashMap()

    init {
        colors[LuaTokenTypes.LONGCOMMENT] = LONGCOMMENT
        colors[LuaElementTypes.LONGCOMMENT_BEGIN] = LONGCOMMENT_BRACES
        colors[LuaElementTypes.LONGCOMMENT_END] = LONGCOMMENT_BRACES
        colors[LuaElementTypes.SHORTCOMMENT] = COMMENT
        colors[LuaElementTypes.SHEBANG] = COMMENT
        colors[LuaTokenTypes.LUADOC_COMMENT] = COMMENT

        colors[LuaElementTypes.STRING] = STRING
        colors[LuaTokenTypes.LONGSTRING] = LONGSTRING
        colors[LuaTokenTypes.LONGSTRING_BEGIN] = LONGSTRING_BRACES
        colors[LuaTokenTypes.LONGSTRING_END] = LONGSTRING_BRACES

        fillMap(colors, LuaSyntax.OPERATORS_SET, OPERATORS)
        fillMap(colors, LuaSyntax.KEYWORDS, KEYWORD)
        fillMap(colors, LuaSyntax.PARENS, PARENTHESES)
        fillMap(colors, LuaSyntax.BRACES, BRACES)
        fillMap(colors, LuaSyntax.BRACKS, BRACKETS)

        colors[LuaElementTypes.SEMI] = SEMI

        fillMap(colors, LuaSyntax.BAD_INPUT, BAD_CHARACTER)
        fillMap(colors, LuaSyntax.DEFINED_CONSTANTS, DEFINED_CONSTANTS)
        colors[LuaElementTypes.COMMA] = COMMA
        colors[LuaElementTypes.NUMBER] = NUMBER
    }

    override fun getHighlightingLexer(): Lexer {
        return LuaLexer()
    }

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return pack(colors[tokenType])
    }

    companion object {
        val LOCAL_VAR: TextAttributesKey =
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
}
