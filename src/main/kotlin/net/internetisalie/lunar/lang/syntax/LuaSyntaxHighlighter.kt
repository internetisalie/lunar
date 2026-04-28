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
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.lexer.LuaLexer
import net.internetisalie.lunar.lang.lexer.LuaTokenTypes
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLazyElementTypes

/**
 * Created by IntelliJ IDEA.
 * User: Max
 * Date: 06.07.2009
 * Time: 16:40:05
 */
class LuaSyntaxHighlighter : SyntaxHighlighterBase() {
    private val colors: MutableMap<IElementType, TextAttributesKey> = HashMap()

    init {
        colors[LuaElementTypes.LONGCOMMENT] = LuaHighlight.LONGCOMMENT
        colors[LuaElementTypes.SHORTCOMMENT] = LuaHighlight.COMMENT
        colors[LuaElementTypes.SHEBANG] = LuaHighlight.COMMENT
        colors[LuaLazyElementTypes.LUACATS_COMMENT] = LuaHighlight.DOC_COMMENT
        colors[LuaElementTypes.STRING] = LuaHighlight.STRING
        colors[LuaElementTypes.ATTRIB_NAME] = LuaHighlight.ATTRIB_NAME

        fillMap(colors, LuaSyntax.OperatorTokens, LuaHighlight.OPERATORS)
        fillMap(colors, LuaSyntax.KeywordTokens, LuaHighlight.KEYWORD)
        fillMap(colors, LuaSyntax.ParenthesisTokens, LuaHighlight.PARENTHESES)
        fillMap(colors, LuaSyntax.BraceTokens, LuaHighlight.BRACES)
        fillMap(colors, LuaSyntax.BracketTokens, LuaHighlight.BRACKETS)

        colors[LuaElementTypes.SEMI] = LuaHighlight.SEMI
        colors[LuaElementTypes.COMMA] = LuaHighlight.COMMA
        colors[LuaElementTypes.ELLIPSIS] = LuaHighlight.ELLIPSIS
        colors[LuaElementTypes.CONCAT] = LuaHighlight.CONCAT

        fillMap(colors, LuaSyntax.BadInputTokens, LuaHighlight.BAD_CHARACTER)
        fillMap(colors, LuaSyntax.PredefinedConstantTokens, LuaHighlight.DEFINED_CONSTANTS)
        colors[LuaElementTypes.NUMBER] = LuaHighlight.NUMBER

        // Identifiers
        fillMap(colors, LuaSyntax.LabelTokens, LuaHighlight.LABEL)
    }

    override fun getHighlightingLexer(): Lexer {
        return LuaLexer()
    }

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return pack(colors[tokenType])
    }
}
