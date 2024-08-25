package net.internetisalie.lunar.lang.lexer

import com.intellij.psi.tree.IElementType
import kotlin.test.Test
import kotlin.test.assertEquals

class Test_LuaLexer {

    data class Token(val offset: Int, val contents:String?, val element:IElementType?)

    fun test_lexer(input: String, vararg expected: Token) {
        var lexer = _LuaLexer(null)
        lexer.reset(input, 0, input.length, _LuaLexer.YYINITIAL)
        for (token in expected) {
            var element = lexer.advance()

            assertEquals(token.element, element)
            assertEquals(token.contents, lexer.yytext())
        }
    }

    @Test
    fun test_strings() {
        test_lexer("'apos'",
            Token(0, "'apos'", LuaTokenTypes.STRING),
        )
    }

}