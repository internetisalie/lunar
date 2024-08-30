/*
 * Copyright 2011 Jon S Akhtar (Sylvanaar)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package net.internetisalie.lunar.luadoc.lang.syntax

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.luadoc.lang.lexer.LuaDocLexer

/**
 * @author ilyas
 */
class LuaDocSyntaxHighlighter : SyntaxHighlighterBase() {
    private val colors: MutableMap<IElementType, TextAttributesKey> = HashMap()

    init {
        fillMap(colors, LuaDocSyntax.COMMENT_CONTENT, LuaDocHighlight.LUADOC)
        fillMap(colors, LuaDocSyntax.COMMENT_TAGS, LuaDocHighlight.LUADOC_TAG)
        fillMap(colors, LuaDocSyntax.COMMENT_VALUES, LuaDocHighlight.LUADOC_VALUE)
    }

    override fun getHighlightingLexer(): Lexer {
        return LuaDocLexer()
    }

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return pack(colors[tokenType])
    }
}
