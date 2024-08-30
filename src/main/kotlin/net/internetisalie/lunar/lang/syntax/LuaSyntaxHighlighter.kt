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
        colors[LuaTokenTypes.LONGCOMMENT] = LuaHighlight.LONGCOMMENT
        colors[LuaElementTypes.LONGCOMMENT_BEGIN] = LuaHighlight.LONGCOMMENT_BRACES
        colors[LuaElementTypes.LONGCOMMENT_END] = LuaHighlight.LONGCOMMENT_BRACES
        colors[LuaElementTypes.SHORTCOMMENT] = LuaHighlight.COMMENT
        colors[LuaElementTypes.SHEBANG] = LuaHighlight.COMMENT
        colors[LuaTokenTypes.LUADOC_COMMENT] = LuaHighlight.COMMENT

        colors[LuaElementTypes.STRING] = LuaHighlight.STRING
        colors[LuaTokenTypes.LONGSTRING] = LuaHighlight.LONGSTRING
        colors[LuaTokenTypes.LONGSTRING_BEGIN] = LuaHighlight.LONGSTRING_BRACES
        colors[LuaTokenTypes.LONGSTRING_END] = LuaHighlight.LONGSTRING_BRACES

        fillMap(colors, LuaSyntax.OPERATORS_SET, LuaHighlight.OPERATORS)
        fillMap(colors, LuaSyntax.KEYWORDS, LuaHighlight.KEYWORD)
        fillMap(colors, LuaSyntax.PARENS, LuaHighlight.PARENTHESES)
        fillMap(colors, LuaSyntax.BRACES, LuaHighlight.BRACES)
        fillMap(colors, LuaSyntax.BRACKS, LuaHighlight.BRACKETS)

        colors[LuaElementTypes.SEMI] = LuaHighlight.SEMI

        fillMap(colors, LuaSyntax.BAD_INPUT, LuaHighlight.BAD_CHARACTER)
        fillMap(colors, LuaSyntax.DEFINED_CONSTANTS, LuaHighlight.DEFINED_CONSTANTS)
        colors[LuaElementTypes.COMMA] = LuaHighlight.COMMA
        colors[LuaElementTypes.NUMBER] = LuaHighlight.NUMBER
    }

    override fun getHighlightingLexer(): Lexer {
        return LuaLexer()
    }

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return pack(colors[tokenType])
    }
}
