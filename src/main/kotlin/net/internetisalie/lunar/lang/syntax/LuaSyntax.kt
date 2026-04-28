/*
 * Copyright 2010 Jon S Akhtar (Sylvanaar)
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

package net.internetisalie.lunar.lang.syntax

import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import net.internetisalie.lunar.lang.lexer.LuaTokenTypes
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLazyElementTypes
import net.internetisalie.lunar.luacats.lang.lexer.LuaCatsTokenTypes

/**
 * High-level syntactic and semantic elements of Lua.
 */
object LuaSyntax {
    val IdentifierTokens: TokenSet = TokenSet.create(
        LuaTokenTypes.IDENTIFIER,
        LuaElementTypes.IDENTIFIER
    )

    val CommentTokens: TokenSet = TokenSet.create(
        LuaElementTypes.SHORTCOMMENT,
        LuaElementTypes.LONGCOMMENT,
        LuaElementTypes.SHEBANG,
        LuaLazyElementTypes.LUACATS_COMMENT,
    )

    val StringLiteralTokens: TokenSet = TokenSet.create(
        LuaElementTypes.STRING,
    )

    val KeywordTokens: TokenSet = TokenSet.create(
        LuaElementTypes.DO,
        LuaElementTypes.FUNCTION,
        LuaElementTypes.NOT,
        LuaElementTypes.AND,
        LuaElementTypes.OR,
        LuaElementTypes.GOTO,
        LuaTokenTypes.WITH,
        LuaElementTypes.IF,
        LuaElementTypes.THEN,
        LuaElementTypes.ELSEIF,
        LuaElementTypes.THEN,
        LuaElementTypes.ELSE,
        LuaElementTypes.WHILE,
        LuaElementTypes.FOR,
        LuaElementTypes.IN,
        LuaElementTypes.RETURN,
        LuaElementTypes.BREAK,
        LuaTokenTypes.CONTINUE,
        LuaElementTypes.LOCAL,
        LuaElementTypes.REPEAT,
        LuaElementTypes.UNTIL,
        LuaElementTypes.END,
    )

    val BraceTokens: TokenSet = TokenSet.create(LuaElementTypes.LCURLY, LuaElementTypes.RCURLY)
    val ParenthesisTokens: TokenSet = TokenSet.create(LuaElementTypes.LPAREN, LuaElementTypes.RPAREN)
    val BracketTokens: TokenSet = TokenSet.create(LuaElementTypes.LBRACK, LuaElementTypes.RBRACK)

    val BadInputTokens: TokenSet = TokenSet.create(TokenType.BAD_CHARACTER /*LuaElementTypes.UNTERMINATED_STRING*/)

    val LabelTokens: TokenSet = TokenSet.create(LuaElementTypes.LABEL_REF, LuaElementTypes.LABEL_NAME)

    val PredefinedConstantTokens: TokenSet = TokenSet.create(LuaElementTypes.NIL, LuaElementTypes.TRUE, LuaElementTypes.FALSE)

    val UnaryOperatorTokens: TokenSet = TokenSet.create(
        LuaElementTypes.MINUS,
        LuaElementTypes.GETN,
        LuaElementTypes.NOT,
        LuaElementTypes.NEG,
    )

    val AdditiveBinaryOperatorTokens: TokenSet = TokenSet.create(
        LuaElementTypes.MINUS,
        LuaElementTypes.PLUS,
    )

    val MultiplicativeBinaryOperatorTokens: TokenSet = TokenSet.create(
        LuaElementTypes.DIV,
        LuaElementTypes.MULT,
        LuaElementTypes.INTDIV,
        LuaElementTypes.EXP,
        LuaElementTypes.MOD,
    )

    val StringBinaryOperatorTokens: TokenSet = TokenSet.create(
        LuaElementTypes.CONCAT
    )

    val RelationalBinaryOperatorTokens: TokenSet = TokenSet.create(
        LuaElementTypes.EQ,
        LuaElementTypes.GE,
        LuaElementTypes.GT,
        LuaElementTypes.LT,
        LuaElementTypes.LE,
        LuaElementTypes.NE
    )

    val BitwiseBinaryOperatorTokens: TokenSet = TokenSet.create(
        LuaElementTypes.AMP,
        LuaElementTypes.PIPE,
        LuaElementTypes.NEG,
    )

    val ShiftBinaryOperatorTokens: TokenSet = TokenSet.create(
        LuaElementTypes.BSL,
        LuaElementTypes.BSR,
    )

    val LogicalBinaryOperatorTokens: TokenSet = TokenSet.create(
        LuaElementTypes.AND,
        LuaElementTypes.OR,
    )

    val WhiteSpaceTokens: TokenSet = TokenSet.create(
        LuaTokenTypes.WS,
        LuaTokenTypes.NEWLINE,
        TokenType.WHITE_SPACE,
        LuaTokenTypes.NL_BEFORE_LONGSTRING,
        LuaCatsTokenTypes.LCATS_WHITESPACE,
    )

    val OperatorTokens: TokenSet =
        TokenSet.orSet(
            AdditiveBinaryOperatorTokens,
            MultiplicativeBinaryOperatorTokens,
            UnaryOperatorTokens,
            RelationalBinaryOperatorTokens,
            StringBinaryOperatorTokens,
            BitwiseBinaryOperatorTokens,
            ShiftBinaryOperatorTokens,
            LogicalBinaryOperatorTokens,
            TokenSet.create(LuaElementTypes.ASSIGN)
        )

    val PunctuationTokens: TokenSet = TokenSet.create(
        LuaElementTypes.COMMA,
        LuaElementTypes.SEMI,
        LuaElementTypes.ELLIPSIS,
        LuaElementTypes.CONCAT,
    )

    val StatementTokens : TokenSet = TokenSet.create(
        LuaElementTypes.ASSIGNMENT_STATEMENT,
        LuaElementTypes.BREAK_STATEMENT,
        LuaElementTypes.DO_STATEMENT,
        LuaElementTypes.EMPTY_STATEMENT,
        LuaElementTypes.FINAL_STATEMENT,
        LuaElementTypes.FUNC_CALL_STATEMENT,
        LuaElementTypes.FUNC_DECL,
        LuaElementTypes.GENERIC_FOR_STATEMENT,
        LuaElementTypes.GOTO_STATEMENT,
        LuaElementTypes.IF_STATEMENT,
        LuaElementTypes.LABEL,
        LuaElementTypes.LOCAL_FUNC_DECL,
        LuaElementTypes.LOCAL_VAR_DECL,
        LuaElementTypes.NUMERIC_FOR_STATEMENT,
        LuaElementTypes.REPEAT_STATEMENT,
        LuaElementTypes.STATEMENT,
        LuaElementTypes.WHILE_STATEMENT
    )
}