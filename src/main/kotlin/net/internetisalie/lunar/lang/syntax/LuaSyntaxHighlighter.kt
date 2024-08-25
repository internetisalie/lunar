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
    private val highlight = LuaHighlight

    init {
        colors[LuaTokenTypes.LONGCOMMENT] = highlight.LONGCOMMENT
        colors[LuaElementTypes.LONGCOMMENT_BEGIN] = highlight.LONGCOMMENT_BRACES
        colors[LuaElementTypes.LONGCOMMENT_END] = highlight.LONGCOMMENT_BRACES
        colors[LuaElementTypes.SHORTCOMMENT] = highlight.COMMENT
        colors[LuaElementTypes.SHEBANG] = highlight.COMMENT
        colors[LuaTokenTypes.LUADOC_COMMENT] = highlight.COMMENT

        colors[LuaElementTypes.STRING] = highlight.STRING
        colors[LuaTokenTypes.LONGSTRING] = highlight.LONGSTRING
        colors[LuaTokenTypes.LONGSTRING_BEGIN] = highlight.LONGSTRING_BRACES
        colors[LuaTokenTypes.LONGSTRING_END] = highlight.LONGSTRING_BRACES

        fillMap(colors, LuaSyntax.OPERATORS_SET, highlight.OPERATORS)
        fillMap(colors, LuaSyntax.KEYWORDS, highlight.KEYWORD)
        fillMap(colors, LuaSyntax.PARENS, highlight.PARENTHESES)
        fillMap(colors, LuaSyntax.BRACES, highlight.BRACES)
        fillMap(colors, LuaSyntax.BRACKS, highlight.BRACKETS)

        colors[LuaElementTypes.SEMI] = highlight.SEMI

        fillMap(colors, LuaSyntax.BAD_INPUT, highlight.BAD_CHARACTER)
        fillMap(colors, LuaSyntax.DEFINED_CONSTANTS, highlight.DEFINED_CONSTANTS)
        colors[LuaElementTypes.COMMA] = highlight.COMMA
        colors[LuaElementTypes.NUMBER] = highlight.NUMBER
    }

    override fun getHighlightingLexer(): Lexer {
        return LuaLexer()
    }

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return pack(colors[tokenType])
    }

    companion object {

    }
}
