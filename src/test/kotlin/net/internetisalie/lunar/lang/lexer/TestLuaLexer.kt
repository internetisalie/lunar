package net.internetisalie.lunar.lang.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import kotlin.test.Test
import kotlin.test.assertEquals


class TestLuaLexer {
    data class TestCase(
        val name: String,
        val input: String,
        val expected: List<Token>,
    ) {
        fun execute() {
            val lexer = LuaLexer()
            lexer.start(input) //, 0, input.length, _LuaLexer.YYINITIAL)
            for (token in expected) {
                lexer.advance()
                assertEquals(token.offset, lexer.tokenStart, "${name}: unexpected offset")
                assertEquals(token.elementType, lexer.tokenType, "${name}: unexpected element type")
                assertEquals(token.contents, lexer.tokenText, "${name}: unexpected contents")
            }
            lexer.advance()
            assertEquals(null, lexer.tokenType, "${name}: more unconsumed tokens")
        }

    }

    data class Token(val offset: Int, val contents: String?, val elementType: IElementType?)

    fun execute(vararg cases: TestCase) {
        cases.forEach { it.execute() }
    }

    @Test
    fun `short strings`() {
        execute(
            TestCase(
                "single-quoted",
                "'apos'",
                listOf(
                    Token(0, "'apos'", LuaElementTypes.STRING),
                ),
            ),
            TestCase(
                "double-quoted",
                "\"apos\"",
                listOf(
                    Token(0, "\"apos\"", LuaElementTypes.STRING),
                ),
            ),
        )
    }

    @Test
    fun `long strings`() {
        execute(
            TestCase(
                "zero-sep",
                "[[Links]]",
                listOf(
                    Token(0, "[[Links]]", LuaElementTypes.STRING),
                ),
            ),
            TestCase(
                "statement",
                "require [[package.path]]",
                listOf(
                    Token(0, "require", LuaElementTypes.IDENTIFIER),
                    Token(7, " ", TokenType.WHITE_SPACE),
                    Token(8, "[[package.path]]", LuaElementTypes.STRING),
                ),
            ),
            TestCase(
                "opening-newline",
                "[[\nLinks]]",
                listOf(
                    Token(0, "[[\nLinks]]", LuaElementTypes.STRING),
                ),
            ),
            TestCase(
                "one-sep",
                "[=[Links]=]",
                listOf(
                    Token(0, "[=[Links]=]", LuaElementTypes.STRING),
                ),
            ),
            TestCase(
                "four-sep",
                "[====[Links]====]",
                listOf(
                    Token(0, "[====[Links]====]", LuaElementTypes.STRING),
                ),
            ),
            TestCase(
                "nested",
                "[[Lin[=[inside]=]ks]]",
                listOf(
                    Token(0, "[[Lin[=[inside]=]ks]]", LuaElementTypes.STRING),
                ),
            ),
        )
    }

    @Test
    fun `short comments`() {
        execute(
            TestCase(
                "simple",
                "--",
                listOf(
                    Token(0, "--", LuaElementTypes.SHORTCOMMENT),
                ),
            ),
            TestCase(
                "simple",
                "-- hello",
                listOf(
                    Token(0, "-- hello", LuaElementTypes.SHORTCOMMENT),
                ),
            ),
            TestCase(
                "simple",
                "-- hello\n",
                listOf(
                    Token(0, "-- hello", LuaElementTypes.SHORTCOMMENT),
                    Token(8, "\n", TokenType.WHITE_SPACE),
                ),
            ),
        )
    }

    @Test
    fun `long comments`() {
        execute(
            TestCase(
                "zero-sep",
                "--[[ comment ]]",
                listOf(
                    Token(0, "--[[ comment ]]", LuaElementTypes.LONGCOMMENT),
                ),
            ),
            TestCase(
                "multi-line",
                "--[[\ncomment\ncomment\n--]]",
                listOf(
                    Token(0, "--[[\ncomment\ncomment\n--]]", LuaElementTypes.LONGCOMMENT),
                ),
            ),
        )
    }

    @Test
    fun `lua 5 4 attributes`() {
        execute(
            TestCase(
                "delimiters",
                "<>",
                listOf(
                    Token(0, "<", LuaElementTypes.LT),
                    Token(1, ">", LuaElementTypes.GT),
                ),
            ),
            TestCase(
                "keywords",
                "const close",
                listOf(
                    Token(0, "const", LuaElementTypes.IDENTIFIER),
                    Token(5, " ", TokenType.WHITE_SPACE),
                    Token(6, "close", LuaElementTypes.IDENTIFIER),
                ),
            ),
        )
    }

}
