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

package net.internetisalie.lunar.lang.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.psi.LuaElementType

/**
 * Holder of all tokens returned by LuaLexer.
 *
 * @author sylvanaar
 */
object LuaTokenTypes {
    /**
     * Wrong token. Use for debugger needs
     */
    @JvmField val WRONG: IElementType = TokenType.BAD_CHARACTER

    // Whitespaces & NewLines

    @JvmField val NL_BEFORE_LONGSTRING: IElementType = LuaElementType("newline after longstring start bracket")
    @JvmField val WS: IElementType = TokenType.WHITE_SPACE
    @JvmField val NEWLINE: IElementType = LuaElementType("new line")

    // Comments

    @JvmField val MARKER: IElementType = LuaElementType("line label marker")

    @JvmField val SHEBANG: IElementType = LuaElementType("shebang - should ignore")

    @JvmField val LONGCOMMENT: IElementType = LuaElementType("long comment")
    @JvmField val SHORTCOMMENT: IElementType = LuaElementType("short comment")

    @JvmField val LONGCOMMENT_BEGIN: IElementType = LuaElementType("long comment start bracket")
    @JvmField val LONGCOMMENT_END: IElementType = LuaElementType("long comment end bracket")

    // Identifiers

    @JvmField val IDENTIFIER: IElementType = LuaElementType("identifier")

    // Integers & floats

    @JvmField val NUMBER: IElementType = LuaElementType("number")

    // Strings & regular expressions

    @JvmField val STRING: IElementType = LuaElementType("string")
    @JvmField val LONGSTRING: IElementType = LuaElementType("long string")

    @JvmField val LONGSTRING_BEGIN: IElementType = LuaElementType("long string start bracket")
    @JvmField val LONGSTRING_END: IElementType = LuaElementType("long string end bracket")

    // Common tokens: operators, braces etc.

    @JvmField val DIV: IElementType = LuaElementType("/")
    @JvmField val MULT: IElementType = LuaElementType("*")
    @JvmField val LPAREN: IElementType = LuaElementType("(")
    @JvmField val RPAREN: IElementType = LuaElementType(")")
    @JvmField val LBRACK: IElementType = LuaElementType("[")
    @JvmField val RBRACK: IElementType = LuaElementType("]")
    @JvmField val LCURLY: IElementType = LuaElementType("{")
    @JvmField val RCURLY: IElementType = LuaElementType("}")
    @JvmField val COLON: IElementType = LuaElementType(":")
    @JvmField val COMMA: IElementType = LuaElementType(",")
    @JvmField val DOT: IElementType = LuaElementType(".")
    @JvmField val ASSIGN: IElementType = LuaElementType("=")
    @JvmField val SEMI: IElementType = LuaElementType(";")
    @JvmField val EQ: IElementType = LuaElementType("==")
    @JvmField val NE: IElementType = LuaElementType("~=")
    @JvmField val PLUS: IElementType = LuaElementType("+")
    @JvmField val MINUS: IElementType = LuaElementType("-")
    @JvmField val GE: IElementType = LuaElementType(">=")
    @JvmField val GT: IElementType = LuaElementType(">")
    @JvmField val EXP: IElementType = LuaElementType("^")
    @JvmField val LE: IElementType = LuaElementType("<=")
    @JvmField val LT: IElementType = LuaElementType("<")
    @JvmField val ELLIPSIS: IElementType = LuaElementType("...")
    @JvmField val CONCAT: IElementType = LuaElementType("..")
    @JvmField val GETN: IElementType = LuaElementType("#")
    @JvmField val MOD: IElementType = LuaElementType("%")

    // New Operators
    @JvmField val INTDIV: IElementType = LuaElementType("//")
    @JvmField val AMP: IElementType = LuaElementType("&")
    @JvmField val NEG: IElementType = LuaElementType("~")
    @JvmField val PIPE: IElementType = LuaElementType("|")
    @JvmField val BSR: IElementType = LuaElementType(">>")
    @JvmField val BSL: IElementType = LuaElementType("<<")

    // Keywords

    @JvmField val IF: IElementType = LuaElementType("if")
    @JvmField val ELSE: IElementType = LuaElementType("else")
    @JvmField val ELSEIF: IElementType = LuaElementType("elseif")
    @JvmField val WHILE: IElementType = LuaElementType("while")
    @JvmField val WITH: IElementType = LuaElementType("with")

    @JvmField val THEN: IElementType = LuaElementType("then")
    @JvmField val FOR: IElementType = LuaElementType("for")
    @JvmField val IN: IElementType = LuaElementType("in")
    @JvmField val RETURN: IElementType = LuaElementType("return")
    @JvmField val BREAK: IElementType = LuaElementType("break")

    @JvmField val CONTINUE: IElementType = LuaElementType("continue")
    @JvmField val TRUE: IElementType = LuaElementType("true")
    @JvmField val FALSE: IElementType = LuaElementType("false")
    @JvmField val NIL: IElementType = LuaElementType("nil")
    @JvmField val FUNCTION: IElementType = LuaElementType("function")

    @JvmField val DO: IElementType = LuaElementType("do")
    @JvmField val NOT: IElementType = LuaElementType("not")
    @JvmField val AND: IElementType = LuaElementType("and")
    @JvmField val OR: IElementType = LuaElementType("or")
    @JvmField val LOCAL: IElementType = LuaElementType("local")

    @JvmField val REPEAT: IElementType = LuaElementType("repeat")
    @JvmField val UNTIL: IElementType = LuaElementType("until")
    @JvmField val END: IElementType = LuaElementType("end")

    @JvmField val GOTO: IElementType = LuaElementType("goto")
    @JvmField val GLOBAL: IElementType = LuaElementType("global")
}
