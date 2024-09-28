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
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import net.internetisalie.lunar.lang.lexer.LuaTokenTypes
import net.internetisalie.lunar.lang.psi.LuaElementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.luacats.lang.lexer.LuaCatsTokenTypes
import net.internetisalie.lunar.luacats.lang.lexer.LuaLazyElementTypes
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes
import net.internetisalie.lunar.luadoc.lang.lexer.LuaDocTokenTypes
import net.internetisalie.lunar.luadoc.lang.parser.LuaDocElementTypes

/**
 * High-level syntactic and semantic elements of Lua.
 */
object LuaSyntax {
    val EMPTY_INPUT: IElementType = LuaElementType("empty input")

    val FUNCTION_DEFINITION: IElementType = LuaElementType("Function Definition")

    val LOCAL_NAME: IElementType = LuaElementType("local name")
    val LOCAL_NAME_DECL: IElementType = LuaElementType("local name declaration")

    val GLOBAL_NAME: IElementType = LuaElementType("global name")

    //  IElementType GLOBAL_NAME_DECL = new LuaElementType("global name declaration");
    // IElementType GETTABLE = new LuaElementType("get table");
    //IElementType GETSELF = new LuaElementType("get self");
    //    LuaStubGlobalDeclarationType GLOBAL_NAME_DECL = new LuaStubGlobalDeclarationType();
    //    LuaStubModuleDeclarationType MODULE_NAME_DECL = new LuaStubModuleDeclarationType();
    //    LuaStubCompoundIdentifierType GETTABLE = new LuaStubCompoundIdentifierType();
    //LuaStubElementType<LuaCompoundIdentifierStub, LuaCompoundIdentifier> GETSELF = new
    // LuaStubCompoundIdentifierType();
    //    LuaFieldStubType FIELD_NAME = new LuaFieldStubType();
    //    IElementType FILE = LuaParserDefinition.LUA_FILE;
    val TABLE_INDEX: IElementType = LuaElementType("table index")
    val KEY_ASSIGNMENT: IElementType = LuaElementType("keyed field initializer")
    val IDX_ASSIGNMENT: IElementType = LuaElementType("indexed field initializer")

    //    IElementType REFERENCE = new LuaElementType("Reference");
    val COMPOUND_REFERENCE: IElementType = LuaElementType("Compound Reference")
    val IDENTIFIER_LIST: IElementType = LuaElementType("Identifier List")

    val STATEMENT: IElementType = LuaElementType("Statment")
    val LAST_STATEMENT: IElementType = LuaElementType("LastStatement")
    val EXPR: IElementType = LuaElementType("Expression")
    val EXPR_LIST: IElementType = LuaElementType("Expression List")

    val LITERAL_EXPRESSION: IElementType = LuaElementType("Literal Expression")
    val PARENTHEICAL_EXPRESSION: IElementType = LuaElementType("Parentheical Expression")

    //    LuaTableStubType TABLE_CONSTRUCTOR = new LuaTableStubType();
    val FUNCTION_CALL_ARGS: IElementType = LuaElementType("Function Call Args")
    val FUNCTION_CALL: IElementType = LuaElementType("Function Call Statement")
    val FUNCTION_CALL_EXPR: IElementType = LuaElementType("Function Call Expression")
    val ANONYMOUS_FUNCTION_EXPRESSION: IElementType = LuaElementType("Anonymous function expression")

    val ASSIGN_STMT: IElementType = LuaElementType("Assignment Statement")
    val CONDITIONAL_EXPR: IElementType = LuaElementType("Conditional Expression")

    val LOCAL_DECL_WITH_ASSIGNMENT: IElementType = LuaElementType("Local Declaration With Assignment Statement")
    val LOCAL_DECL: IElementType = LuaElementType("Local Declaration")

    val SELF_PARAMETER: IElementType = LuaElementType("Implied parameter (self)")

    val BLOCK: IElementType = LuaElementType("Block")

    val UNARY_EXP: IElementType = LuaElementType("UnExp")
    val BINARY_EXP: IElementType = LuaElementType("BinExp")
    val UNARY_OP: IElementType = LuaElementType("UnOp")
    val BINARY_OP: IElementType = LuaElementType("BinOp")

    val DO_BLOCK: IElementType = LuaElementType("Do Block")

    val WHILE_BLOCK: IElementType = LuaElementType("While Block")

    val REPEAT_BLOCK: IElementType = LuaElementType("Repeat Block")
    val UNTIL_CLAUSE: IElementType = LuaElementType("Until Clause")
    val GENERIC_FOR_BLOCK: IElementType = LuaElementType("Generic For Block")
    val IF_THEN_BLOCK: IElementType = LuaElementType("If-Then Block")
    val NUMERIC_FOR_BLOCK: IElementType = LuaElementType("Numeric For Block")

    val EXPRESSION_SET: TokenSet = TokenSet.create(
        LITERAL_EXPRESSION, BINARY_EXP,
        UNARY_EXP, EXPR, ANONYMOUS_FUNCTION_EXPRESSION, FUNCTION_CALL_EXPR, PARENTHEICAL_EXPRESSION
    )
    val RETURN_STATEMENT: IElementType = LuaElementType("Return statement")
    val RETURN_STATEMENT_WITH_TAIL_CALL: IElementType = LuaElementType("Tailcall Return statement")

    val LOCAL_FUNCTION: IElementType = LuaElementType("local function def")

    val BLOCK_SET: TokenSet = TokenSet.create(
        FUNCTION_DEFINITION, LOCAL_FUNCTION, ANONYMOUS_FUNCTION_EXPRESSION,
        WHILE_BLOCK,
        GENERIC_FOR_BLOCK,
        IF_THEN_BLOCK,
        NUMERIC_FOR_BLOCK,
        REPEAT_BLOCK,
        DO_BLOCK
    )

    val PARAMETER: IElementType = LuaElementType("function parameters")
    val PARAMETER_LIST: IElementType = LuaElementType("function parameter")

    val UPVAL_NAME: IElementType = LuaElementType("upvalue name")
    val MAIN_CHUNK_VARARGS: IElementType = LuaElementType("main chunk args")

    val IDENTIFIER_TOKEN_SET: TokenSet = TokenSet.create(
        LuaTokenTypes.IDENTIFIER,
        LuaElementTypes.IDENTIFIER
    )

    val IDENTIFIER_SET: TokenSet = TokenSet.create(
        LuaElementTypes.LABEL_NAME,
        LuaElementTypes.LABEL_REF,
        LuaElementTypes.IDENTIFIER,
    )

    val COMMENT_SET: TokenSet = TokenSet.create(
        LuaElementTypes.SHORTCOMMENT,
        LuaTokenTypes.LONGCOMMENT,
        LuaElementTypes.SHEBANG,
        LuaElementTypes.LONGCOMMENT_BEGIN,
        LuaElementTypes.LONGCOMMENT_END,
        LuaDocElementTypes.LUADOC_COMMENT,
        LuaLazyElementTypes.LUACATS_COMMENT,
    )

    val STRING_LITERAL_SET: TokenSet = TokenSet.create(
        LuaElementTypes.STRING,
        LuaTokenTypes.LONGSTRING,
        LuaTokenTypes.LONGSTRING_BEGIN,
        LuaTokenTypes.LONGSTRING_END
    )

    val KEYWORDS: TokenSet = TokenSet.create(
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

    val BRACES: TokenSet = TokenSet.create(LuaElementTypes.LCURLY, LuaElementTypes.RCURLY)
    val PARENS: TokenSet = TokenSet.create(LuaElementTypes.LPAREN, LuaElementTypes.RPAREN)
    val BRACKS: TokenSet = TokenSet.create(LuaElementTypes.LBRACK, LuaElementTypes.RBRACK)

    val BAD_INPUT: TokenSet = TokenSet.create(TokenType.BAD_CHARACTER /*LuaElementTypes.UNTERMINATED_STRING*/)

    val LABELS: TokenSet = TokenSet.create(LuaElementTypes.LABEL_REF, LuaElementTypes.LABEL_NAME)

    val DEFINED_CONSTANTS: TokenSet = TokenSet.create(LuaElementTypes.NIL, LuaElementTypes.TRUE, LuaElementTypes.FALSE)

    val UNARY_OP_SET: TokenSet = TokenSet.create(LuaElementTypes.MINUS, LuaElementTypes.GETN)

    val BINARY_OP_SET: TokenSet = TokenSet.create(
        LuaElementTypes.MINUS,
        LuaElementTypes.PLUS,
        LuaElementTypes.DIV,
        LuaElementTypes.MULT,
        LuaElementTypes.EXP,
        LuaElementTypes.MOD,
        LuaElementTypes.CONCAT
    )

    val BLOCK_OPEN_SET: TokenSet = TokenSet.create(
        LuaElementTypes.THEN,
        LuaElementTypes.RPAREN,
        LuaElementTypes.DO,
        LuaElementTypes.ELSE,
        LuaElementTypes.ELSEIF
    )

    val BLOCK_CLOSE_SET: TokenSet =
        TokenSet.create(LuaElementTypes.END, LuaElementTypes.ELSE, LuaElementTypes.ELSEIF, LuaElementTypes.UNTIL)

    val COMPARE_OPS: TokenSet = TokenSet.create(
        LuaElementTypes.EQ,
        LuaElementTypes.GE,
        LuaElementTypes.GT,
        LuaElementTypes.LT,
        LuaElementTypes.LE,
        LuaElementTypes.NE
    )

    val LOGICAL_OPS: TokenSet = TokenSet.create(LuaElementTypes.AND, LuaElementTypes.OR, LuaElementTypes.NOT)

    val ARITHMETIC_OPS: TokenSet = TokenSet.create(
        LuaElementTypes.MINUS,
        LuaElementTypes.PLUS,
        LuaElementTypes.DIV,
        LuaElementTypes.EXP,
        LuaElementTypes.MOD
    )

    val TABLE_ACCESS: TokenSet = TokenSet.create(LuaElementTypes.DOT, LuaElementTypes.COLON, LuaElementTypes.LBRACK)

    val LITERALS_SET: TokenSet = TokenSet.create(
        LuaElementTypes.NUMBER,
        LuaElementTypes.NIL,
        LuaElementTypes.TRUE,
        LuaElementTypes.FALSE,
        LuaElementTypes.STRING,
        LuaTokenTypes.LONGSTRING,
        LuaTokenTypes.LONGSTRING_BEGIN,
        LuaTokenTypes.LONGSTRING_END
    )

    val WHITE_SPACES_SET: TokenSet = TokenSet.create(
        LuaTokenTypes.WS,
        LuaTokenTypes.NEWLINE,
        TokenType.WHITE_SPACE,
        LuaTokenTypes.NL_BEFORE_LONGSTRING,
        LuaDocTokenTypes.LDOC_WHITESPACE,
        LuaCatsTokenTypes.LCATS_WHITESPACE,
    )

    val WHITE_SPACES_OR_COMMENTS: TokenSet = TokenSet.orSet(WHITE_SPACES_SET, COMMENT_SET)

    val COMMENT_AND_WHITESPACE_SET: TokenSet = TokenSet.orSet(COMMENT_SET, WHITE_SPACES_SET)

    val OPERATORS_SET: TokenSet =
        TokenSet.orSet(BINARY_OP_SET, UNARY_OP_SET, COMPARE_OPS, TokenSet.create(LuaElementTypes.ASSIGN))
}