package net.internetisalie.lunar.lang.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLazyElementTypes
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * Exhaustive lexer tests based on Lua 5.4 language specification.
 * Tests all token types, operators, literals, and edge cases.
 */
class TestLuaLexerExhaustive {
    data class TestCase(
        val name: String,
        val input: String,
        val expected: List<Token>,
    ) {
        fun execute() {
            val lexer = LuaLexer()
            lexer.start(input)
            for (token in expected) {
                lexer.advance()
                assertEquals(token.offset, lexer.tokenStart, "${name}: unexpected offset at token $token")
                assertEquals(token.elementType, lexer.tokenType, "${name}: unexpected element type for '${lexer.tokenText}'")
                assertEquals(token.contents, lexer.tokenText, "${name}: unexpected contents")
            }
            lexer.advance()
            assertEquals(null, lexer.tokenType, "${name}: more unconsumed tokens, found '${lexer.tokenText}'")
        }
    }

    data class Token(val offset: Int, val contents: String?, val elementType: IElementType?)

    fun execute(vararg cases: TestCase) {
        cases.forEach { it.execute() }
    }

    // ==================== Keywords ====================

    @Test
    fun `keywords - control flow`() {
        execute(
            TestCase("if", "if", listOf(Token(0, "if", LuaElementTypes.IF))),
            TestCase("then", "then", listOf(Token(0, "then", LuaElementTypes.THEN))),
            TestCase("else", "else", listOf(Token(0, "else", LuaElementTypes.ELSE))),
            TestCase("elseif", "elseif", listOf(Token(0, "elseif", LuaElementTypes.ELSEIF))),
            TestCase("end", "end", listOf(Token(0, "end", LuaElementTypes.END))),
            TestCase("while", "while", listOf(Token(0, "while", LuaElementTypes.WHILE))),
            TestCase("do", "do", listOf(Token(0, "do", LuaElementTypes.DO))),
            TestCase("for", "for", listOf(Token(0, "for", LuaElementTypes.FOR))),
            TestCase("in", "in", listOf(Token(0, "in", LuaElementTypes.IN))),
            TestCase("repeat", "repeat", listOf(Token(0, "repeat", LuaElementTypes.REPEAT))),
            TestCase("until", "until", listOf(Token(0, "until", LuaElementTypes.UNTIL))),
            TestCase("break", "break", listOf(Token(0, "break", LuaElementTypes.BREAK))),
            TestCase("return", "return", listOf(Token(0, "return", LuaElementTypes.RETURN))),
            TestCase("goto", "goto", listOf(Token(0, "goto", LuaElementTypes.GOTO))),
        )
    }

    @Test
    fun `keywords - function declaration`() {
        execute(
            TestCase("function", "function", listOf(Token(0, "function", LuaElementTypes.FUNCTION))),
            TestCase("local", "local", listOf(Token(0, "local", LuaElementTypes.LOCAL))),
        )
    }

    @Test
    fun `keywords - constants and logical`() {
        execute(
            TestCase("nil", "nil", listOf(Token(0, "nil", LuaElementTypes.NIL))),
            TestCase("true", "true", listOf(Token(0, "true", LuaElementTypes.TRUE))),
            TestCase("false", "false", listOf(Token(0, "false", LuaElementTypes.FALSE))),
            TestCase("and", "and", listOf(Token(0, "and", LuaElementTypes.AND))),
            TestCase("or", "or", listOf(Token(0, "or", LuaElementTypes.OR))),
            TestCase("not", "not", listOf(Token(0, "not", LuaElementTypes.NOT))),
        )
    }

    // ==================== Operators ====================

    @Test
    fun `operators - comparison`() {
        execute(
            TestCase("==", "==", listOf(Token(0, "==", LuaElementTypes.EQ))),
            TestCase("~=", "~=", listOf(Token(0, "~=", LuaElementTypes.NE))),
            TestCase("<", "<", listOf(Token(0, "<", LuaElementTypes.LT))),
            TestCase(">", ">", listOf(Token(0, ">", LuaElementTypes.GT))),
            TestCase("<=", "<=", listOf(Token(0, "<=", LuaElementTypes.LE))),
            TestCase(">=", ">=", listOf(Token(0, ">=", LuaElementTypes.GE))),
        )
    }

    @Test
    fun `operators - string and assignment`() {
        execute(
            TestCase("..", "..", listOf(Token(0, "..", LuaElementTypes.CONCAT))),
            TestCase("=", "=", listOf(Token(0, "=", LuaElementTypes.ASSIGN))),
            TestCase("#", "#", listOf(Token(0, "#", LuaElementTypes.GETN))),
        )
    }

    @Test
    fun `punctuation`() {
        execute(
            TestCase("(", "(", listOf(Token(0, "(", LuaElementTypes.LPAREN))),
            TestCase(")", ")", listOf(Token(0, ")", LuaElementTypes.RPAREN))),
            TestCase("{", "{", listOf(Token(0, "{", LuaElementTypes.LCURLY))),
            TestCase("}", "}", listOf(Token(0, "}", LuaElementTypes.RCURLY))),
            TestCase("[", "[", listOf(Token(0, "[", LuaElementTypes.LBRACK))),
            TestCase("]", "]", listOf(Token(0, "]", LuaElementTypes.RBRACK))),
            TestCase(";", ";", listOf(Token(0, ";", LuaElementTypes.SEMI))),
            TestCase(":", ":", listOf(Token(0, ":", LuaElementTypes.COLON))),
            TestCase(",", ",", listOf(Token(0, ",", LuaElementTypes.COMMA))),
            TestCase(".", ".", listOf(Token(0, ".", LuaElementTypes.DOT))),
            TestCase("...", "...", listOf(Token(0, "...", LuaElementTypes.ELLIPSIS))),
        )
    }

    // ==================== Numbers ====================

    @Test
    fun `numbers - integers`() {
        execute(
            TestCase("zero", "0", listOf(Token(0, "0", LuaElementTypes.NUMBER))),
            TestCase("positive", "42", listOf(Token(0, "42", LuaElementTypes.NUMBER))),
            TestCase("large", "999999999", listOf(Token(0, "999999999", LuaElementTypes.NUMBER))),
        )
    }

    @Test
    fun `numbers - floats`() {
        execute(
            TestCase("decimal", "3.14", listOf(Token(0, "3.14", LuaElementTypes.NUMBER))),
            TestCase("leading decimal", ".5", listOf(Token(0, ".5", LuaElementTypes.NUMBER))),
            TestCase("trailing decimal", "5.", listOf(Token(0, "5.", LuaElementTypes.NUMBER))),
        )
    }

    @Test
    fun `numbers - scientific notation`() {
        execute(
            TestCase("e notation", "1e10", listOf(Token(0, "1e10", LuaElementTypes.NUMBER))),
            TestCase("E notation", "2E-5", listOf(Token(0, "2E-5", LuaElementTypes.NUMBER))),
            TestCase("float e notation", "1.5e+3", listOf(Token(0, "1.5e+3", LuaElementTypes.NUMBER))),
        )
    }

    @Test
    fun `numbers - hexadecimal`() {
        execute(
            TestCase("0x prefix", "0x10", listOf(Token(0, "0x10", LuaElementTypes.NUMBER))),
            TestCase("0X prefix", "0XFF", listOf(Token(0, "0XFF", LuaElementTypes.NUMBER))),
        )
    }

    // ==================== Strings ====================

    @Test
    fun `strings - single quoted`() {
        execute(
            TestCase("empty", "''", listOf(Token(0, "''", LuaElementTypes.STRING))),
            TestCase("simple", "'hello'", listOf(Token(0, "'hello'", LuaElementTypes.STRING))),
            TestCase("with spaces", "'hello world'", listOf(Token(0, "'hello world'", LuaElementTypes.STRING))),
        )
    }

    @Test
    fun `strings - double quoted`() {
        execute(
            TestCase("empty", "\"\"", listOf(Token(0, "\"\"", LuaElementTypes.STRING))),
            TestCase("simple", "\"hello\"", listOf(Token(0, "\"hello\"", LuaElementTypes.STRING))),
            TestCase("with spaces", "\"hello world\"", listOf(Token(0, "\"hello world\"", LuaElementTypes.STRING))),
        )
    }

    @Test
    fun `strings - escape sequences`() {
        execute(
            TestCase("newline", "'\\n'", listOf(Token(0, "'\\n'", LuaElementTypes.STRING))),
            TestCase("tab", "'\\t'", listOf(Token(0, "'\\t'", LuaElementTypes.STRING))),
            TestCase("quote", "\"\\\"quote\\\"\"", listOf(Token(0, "\"\\\"quote\\\"\"", LuaElementTypes.STRING))),
            TestCase("backslash", "'\\\\'", listOf(Token(0, "'\\\\'", LuaElementTypes.STRING))),
        )
    }

    @Test
    fun `strings - long strings`() {
        execute(
            TestCase("zero separator", "[[hello]]", listOf(Token(0, "[[hello]]", LuaElementTypes.STRING))),
            TestCase("one separator", "[=[hello]=]", listOf(Token(0, "[=[hello]=]", LuaElementTypes.STRING))),
            TestCase("multi separator", "[====[hello]====]", listOf(Token(0, "[====[hello]====]", LuaElementTypes.STRING))),
            TestCase("with newline", "[[\nhello\nworld\n]]", listOf(Token(0, "[[\nhello\nworld\n]]", LuaElementTypes.STRING))),
        )
    }

    // ==================== Identifiers ====================

    @Test
    fun `identifiers - valid names`() {
        execute(
            TestCase("simple", "x", listOf(Token(0, "x", LuaElementTypes.IDENTIFIER))),
            TestCase("underscore start", "_x", listOf(Token(0, "_x", LuaElementTypes.IDENTIFIER))),
            TestCase("with numbers", "var123", listOf(Token(0, "var123", LuaElementTypes.IDENTIFIER))),
            TestCase("underscore underscore", "__", listOf(Token(0, "__", LuaElementTypes.IDENTIFIER))),
            TestCase("camelCase", "myVariable", listOf(Token(0, "myVariable", LuaElementTypes.IDENTIFIER))),
            TestCase("snake_case", "my_variable", listOf(Token(0, "my_variable", LuaElementTypes.IDENTIFIER))),
        )
    }

    // ==================== Comments ====================

    @Test
    fun `comments - short comments`() {
        execute(
            TestCase("empty", "--", listOf(Token(0, "--", LuaElementTypes.SHORTCOMMENT))),
            TestCase("with text", "-- hello", listOf(Token(0, "-- hello", LuaElementTypes.SHORTCOMMENT))),
            TestCase("with newline", "-- comment\n", listOf(
                Token(0, "-- comment", LuaElementTypes.SHORTCOMMENT),
                Token(10, "\n", TokenType.WHITE_SPACE),
            )),
        )
    }

    @Test
    fun `comments - long comments`() {
        execute(
            TestCase("zero separator", "--[[ comment ]]", listOf(Token(0, "--[[ comment ]]", LuaElementTypes.LONGCOMMENT))),
            TestCase("one separator", "--[=[ comment ]=]", listOf(Token(0, "--[=[ comment ]=]", LuaElementTypes.LONGCOMMENT))),
            TestCase("multi-line", "--[[\nline1\nline2\n]]", listOf(Token(0, "--[[\nline1\nline2\n]]", LuaElementTypes.LONGCOMMENT))),
        )
    }

    @Test
    fun `comments - LuaCats documentation`() {
        execute(
            TestCase("single line", "--- Documentation", listOf(Token(0, "--- Documentation", LuaLazyElementTypes.LUACATS_COMMENT))),
        )
    }

    // ==================== Whitespace ====================

    @Test
    fun `whitespace - various types`() {
        execute(
            TestCase("space", "x y", listOf(
                Token(0, "x", LuaElementTypes.IDENTIFIER),
                Token(1, " ", TokenType.WHITE_SPACE),
                Token(2, "y", LuaElementTypes.IDENTIFIER),
            )),
            TestCase("tab", "x\ty", listOf(
                Token(0, "x", LuaElementTypes.IDENTIFIER),
                Token(1, "\t", TokenType.WHITE_SPACE),
                Token(2, "y", LuaElementTypes.IDENTIFIER),
            )),
            TestCase("newline", "x\ny", listOf(
                Token(0, "x", LuaElementTypes.IDENTIFIER),
                Token(1, "\n", TokenType.WHITE_SPACE),
                Token(2, "y", LuaElementTypes.IDENTIFIER),
            )),
        )
    }

    // ==================== Complex Expressions ====================

    @Test
    fun `complex - assignment`() {
        execute(
            TestCase("simple assignment", "x = 5", listOf(
                Token(0, "x", LuaElementTypes.IDENTIFIER),
                Token(1, " ", TokenType.WHITE_SPACE),
                Token(2, "=", LuaElementTypes.ASSIGN),
                Token(3, " ", TokenType.WHITE_SPACE),
                Token(4, "5", LuaElementTypes.NUMBER),
            )),
            TestCase("function call", "print(42)", listOf(
                Token(0, "print", LuaElementTypes.IDENTIFIER),
                Token(5, "(", LuaElementTypes.LPAREN),
                Token(6, "42", LuaElementTypes.NUMBER),
                Token(8, ")", LuaElementTypes.RPAREN),
            )),
        )
    }

    @Test
    fun `complex - operators mix`() {
        execute(
            TestCase("arithmetic", "1 + 2 * 3", listOf(
                Token(0, "1", LuaElementTypes.NUMBER),
                Token(1, " ", TokenType.WHITE_SPACE),
                Token(2, "+", LuaElementTypes.PLUS),
                Token(3, " ", TokenType.WHITE_SPACE),
                Token(4, "2", LuaElementTypes.NUMBER),
                Token(5, " ", TokenType.WHITE_SPACE),
                Token(6, "*", LuaElementTypes.MULT),
                Token(7, " ", TokenType.WHITE_SPACE),
                Token(8, "3", LuaElementTypes.NUMBER),
            )),
        )
    }

    // ==================== Edge Cases ====================

    @Test
    fun `edge cases - single characters`() {
        execute(
            TestCase("single keyword", "x", listOf(Token(0, "x", LuaElementTypes.IDENTIFIER))),
            TestCase("single number", "5", listOf(Token(0, "5", LuaElementTypes.NUMBER))),
            TestCase("single operator", "+", listOf(Token(0, "+", LuaElementTypes.PLUS))),
        )
    }

    @Test
    fun `edge cases - empty and whitespace only`() {
        execute(
            TestCase("empty", "", listOf()),
            TestCase("spaces only", "   ", listOf(Token(0, "   ", TokenType.WHITE_SPACE))),
        )
    }

    @Test
    fun `edge cases - consecutive operators`() {
        execute(
            TestCase("concat operators", "a..b", listOf(
                Token(0, "a", LuaElementTypes.IDENTIFIER),
                Token(1, "..", LuaElementTypes.CONCAT),
                Token(3, "b", LuaElementTypes.IDENTIFIER),
            )),
        )
    }

    @Test
    fun `edge cases - mixed content`() {
        execute(
            TestCase("function simple", "function add(a, b) return a + b end", listOf(
                Token(0, "function", LuaElementTypes.FUNCTION),
                Token(8, " ", TokenType.WHITE_SPACE),
                Token(9, "add", LuaElementTypes.IDENTIFIER),
                Token(12, "(", LuaElementTypes.LPAREN),
                Token(13, "a", LuaElementTypes.IDENTIFIER),
                Token(14, ",", LuaElementTypes.COMMA),
                Token(15, " ", TokenType.WHITE_SPACE),
                Token(16, "b", LuaElementTypes.IDENTIFIER),
                Token(17, ")", LuaElementTypes.RPAREN),
                Token(18, " ", TokenType.WHITE_SPACE),
                Token(19, "return", LuaElementTypes.RETURN),
                Token(25, " ", TokenType.WHITE_SPACE),
                Token(26, "a", LuaElementTypes.IDENTIFIER),
                Token(27, " ", TokenType.WHITE_SPACE),
                Token(28, "+", LuaElementTypes.PLUS),
                Token(29, " ", TokenType.WHITE_SPACE),
                Token(30, "b", LuaElementTypes.IDENTIFIER),
                Token(31, " ", TokenType.WHITE_SPACE),
                Token(32, "end", LuaElementTypes.END),
            )),
        )
    }
}
