package net.internetisalie.lunar.luacats.lang.lexer

import com.intellij.psi.tree.IElementType
import org.junit.jupiter.api.Assertions.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals

class TestLuaCatsLexer {

    companion object {
        private const val YYINITIAL: Int = _LuaCatsLexer.YYINITIAL
        private const val COMMENT_START: Int = _LuaCatsLexer.COMMENT_START
        private const val COMMENT_DATA: Int = _LuaCatsLexer.COMMENT_DATA
        private const val TAG_CAST: Int = _LuaCatsLexer.TAG_CAST
        private const val TAG_CLASS: Int = _LuaCatsLexer.TAG_CLASS
        private const val TAG_DIAGNOSTIC: Int = _LuaCatsLexer.TAG_DIAGNOSTIC
        private const val TAG_ENUM: Int = _LuaCatsLexer.TAG_ENUM
        private const val TAG_FIELD: Int = _LuaCatsLexer.TAG_FIELD
        private const val TAG_MODULE: Int = _LuaCatsLexer.TAG_MODULE
        private const val TAG_OPERATOR: Int = _LuaCatsLexer.TAG_OPERATOR
        private const val TAG_PARAM: Int = _LuaCatsLexer.TAG_PARAM
        private const val TAG_RETURN: Int = _LuaCatsLexer.TAG_RETURN
        private const val TAG_SEE: Int = _LuaCatsLexer.TAG_SEE
        private const val TAG_SOURCE: Int = _LuaCatsLexer.TAG_SOURCE
        private const val TAG_TYPE: Int = _LuaCatsLexer.TAG_TYPE
        private const val TAG_VERSION: Int = _LuaCatsLexer.TAG_VERSION
    }

    data class Token(val contents: String?, val element: IElementType?)

    private fun testLexer(input: String, state: Int, vararg expected: Token) {
        val lexer = _LuaCatsLexer(null)
        lexer.reset(input, 0, input.length, state)
        for (token in expected) {
            val element = lexer.advance()
            val text = lexer.yytext()

            assertEquals(token.element, element, "mismatched type for token '$text'")
            assertEquals(token.contents, text, "mismatched contents for token '$text'")
        }
        assertNull(lexer.advance())
    }

    @Test
    fun testInitial() {
        testLexer(
            "---", YYINITIAL,
            Token("---", LuaCatsTokenTypes.LCATS_DASHES),
        )

        testLexer(
            "   ", YYINITIAL,
            Token("   ", LuaCatsTokenTypes.LCATS_WHITESPACE),
        )

        testLexer(
            "   ---", YYINITIAL,
            Token("   ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("---", LuaCatsTokenTypes.LCATS_DASHES),
        )
    }

    @Test
    fun testCommentStart() {
        testLexer(
            "@async some text +-. ", COMMENT_START,
            Token("@async", LuaCatsTokenTypes.LCATS_TAG),
            Token(" some text +-. ", LuaCatsTokenTypes.LCATS_TEXT),
        )

        testLexer(
            "@class Animal", COMMENT_START,
            Token("@class", LuaCatsTokenTypes.LCATS_TAG),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("Animal", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "@cast x", COMMENT_START,
            Token("@cast", LuaCatsTokenTypes.LCATS_TAG),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("x", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "@deprecated something", COMMENT_START,
            Token("@deprecated", LuaCatsTokenTypes.LCATS_TAG),
            Token(" something", LuaCatsTokenTypes.LCATS_TEXT),
        )

        testLexer(
            "@diagnostic something", COMMENT_START,
            Token("@diagnostic", LuaCatsTokenTypes.LCATS_TAG),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("something", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "@enum (key) Direction", COMMENT_START,
            Token("@enum", LuaCatsTokenTypes.LCATS_TAG),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("(key)", LuaCatsTokenTypes.LCATS_KEYWORD),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("Direction", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "@field height", COMMENT_START,
            Token("@field", LuaCatsTokenTypes.LCATS_TAG),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("height", LuaCatsTokenTypes.LCATS_NAME),
        )
    }

    @Test
    fun testCommentData() {
        testLexer(
            "This is some text 43.5 +-:?", COMMENT_DATA,
            Token("This is some text 43.5 +-:?", LuaCatsTokenTypes.LCATS_TEXT)
        )

        testLexer(
            "This \n", COMMENT_DATA,
            Token("This ", LuaCatsTokenTypes.LCATS_TEXT),
            Token("\n", LuaCatsTokenTypes.LCATS_WHITESPACE)
        )
    }

    @Test
    fun testCast() {
        testLexer(
            " x +boolean, +number, -?", TAG_CAST,
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("x", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("+", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("boolean", LuaCatsTokenTypes.LCATS_NAME),
            Token(",", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("+", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("number", LuaCatsTokenTypes.LCATS_NAME),
            Token(",", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("-", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("?", LuaCatsTokenTypes.LCATS_SYMBOL),
        )
    }

    @Test
    fun testClass() {
        testLexer(
            " (exact) Animal", TAG_CLASS,
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("(exact)", LuaCatsTokenTypes.LCATS_KEYWORD),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("Animal", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "Dog: Animal, Lifeform", TAG_CLASS,
            Token("Dog", LuaCatsTokenTypes.LCATS_NAME),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("Animal", LuaCatsTokenTypes.LCATS_NAME),
            Token(",", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("Lifeform", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "table<K, V>: { [K]: V }", TAG_CLASS,
            Token("table", LuaCatsTokenTypes.LCATS_NAME),
            Token("<", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("K", LuaCatsTokenTypes.LCATS_NAME),
            Token(",", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("V", LuaCatsTokenTypes.LCATS_NAME),
            Token(">", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("{", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("[", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("K", LuaCatsTokenTypes.LCATS_NAME),
            Token("]", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("V", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("}", LuaCatsTokenTypes.LCATS_SYMBOL),
        )
    }

    @Test
    fun testDiagnostic() {
        testLexer(
            "disable-next-line: unused-local", TAG_DIAGNOSTIC,
            Token("disable-next-line", LuaCatsTokenTypes.LCATS_KEYWORD),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("unused-local", LuaCatsTokenTypes.LCATS_NAME),
        )
    }

    @Test
    fun testEnum() {
        testLexer(
            "Direction", TAG_ENUM,
            Token("Direction", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "(key) Direction", TAG_ENUM,
            Token("(key)", LuaCatsTokenTypes.LCATS_KEYWORD),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("Direction", LuaCatsTokenTypes.LCATS_NAME),
        )
    }

    @Test
    fun testField() {
        testLexer(
            "height number? The height ", TAG_FIELD,
            Token("height", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("number", LuaCatsTokenTypes.LCATS_NAME),
            Token("?", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("The", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("height", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
        )

        testLexer(
            "private height number", TAG_FIELD,
            Token("private", LuaCatsTokenTypes.LCATS_KEYWORD),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("height", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("number", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "[string] integer Description", TAG_FIELD,
            Token("[", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("string", LuaCatsTokenTypes.LCATS_NAME),
            Token("]", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("integer", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("Description", LuaCatsTokenTypes.LCATS_NAME),
        )
    }

    @Test
    fun testModule() {
        testLexer(
            "'modname'", TAG_MODULE,
            Token("'modname'", LuaCatsTokenTypes.LCATS_STRING),
        )
        testLexer(
            """"sdfsd"""", TAG_MODULE,
            Token(""""sdfsd"""", LuaCatsTokenTypes.LCATS_STRING),
        )
    }

    @Test
    fun testParam() {
        testLexer(
            "username string The name", TAG_PARAM,
            Token("username", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("string", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("The", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("name", LuaCatsTokenTypes.LCATS_NAME),
        )
    }

    @Test
    fun testOperator() {
        testLexer(
            "add(Vector): Vector", TAG_OPERATOR,
            Token("add", LuaCatsTokenTypes.LCATS_NAME),
            Token("(", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("Vector", LuaCatsTokenTypes.LCATS_NAME),
            Token(")", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("Vector", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "unm:integer", TAG_OPERATOR,
            Token("unm", LuaCatsTokenTypes.LCATS_NAME),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("integer", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "call:string", TAG_OPERATOR,
            Token("call", LuaCatsTokenTypes.LCATS_NAME),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("string", LuaCatsTokenTypes.LCATS_NAME),
        )
    }

    @Test
    fun testOverload() {
        testLexer(
            "fun(objectID: integer): boolean", TAG_TYPE,
            Token("fun", LuaCatsTokenTypes.LCATS_KEYWORD),
            Token("(", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("objectID", LuaCatsTokenTypes.LCATS_NAME),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("integer", LuaCatsTokenTypes.LCATS_NAME),
            Token(")", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("boolean", LuaCatsTokenTypes.LCATS_NAME),
        )
    }

    @Test
    fun testReturn() {
        testLexer(
            "boolean", TAG_RETURN,
            Token("boolean", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "boolean enabled", TAG_RETURN,
            Token("boolean", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("enabled", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "boolean enabled|nil If the", TAG_RETURN,
            Token("boolean", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("enabled", LuaCatsTokenTypes.LCATS_NAME),
            Token("|", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("nil", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("If", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("the", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "boolean # If the", TAG_RETURN,
            Token("boolean", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("#", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" If the", LuaCatsTokenTypes.LCATS_TEXT),
        )

        testLexer(
            "string ...", TAG_RETURN,
            Token("string", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("...", LuaCatsTokenTypes.LCATS_SYMBOL),
        )
    }

    @Test
    fun testSee() {
        testLexer(
            "http.www:get", TAG_SEE,
            Token("http.www", LuaCatsTokenTypes.LCATS_NAME),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("get", LuaCatsTokenTypes.LCATS_NAME),
        )
    }

    @Test
    fun testSource() {
        testLexer(" file:///C:/Users/me/Documents/program/myFile.c:10 ", TAG_SOURCE,
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("file:///C:/Users/me/Documents/program/myFile.c:10", LuaCatsTokenTypes.LCATS_STRING),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
        )
    }

    @Test
    fun testType() {
        testLexer("boolean", TAG_TYPE,
            Token("boolean", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer("string[]", TAG_TYPE,
            Token("string", LuaCatsTokenTypes.LCATS_NAME),
            Token("[]", LuaCatsTokenTypes.LCATS_SYMBOL),
        )

        testLexer(
            "{ [string]: boolean }", TAG_TYPE,
            Token("{", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("[", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("string", LuaCatsTokenTypes.LCATS_NAME),
            Token("]", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("boolean", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("}", LuaCatsTokenTypes.LCATS_SYMBOL),
        )

        testLexer(
            "table<userID, Player>", TAG_TYPE,
            Token("table", LuaCatsTokenTypes.LCATS_NAME),
            Token("<", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("userID", LuaCatsTokenTypes.LCATS_NAME),
            Token(",", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("Player", LuaCatsTokenTypes.LCATS_NAME),
            Token(">", LuaCatsTokenTypes.LCATS_SYMBOL),
        )

        testLexer(
            """boolean|number|"yes"|"no"""", TAG_TYPE,
            Token("boolean", LuaCatsTokenTypes.LCATS_NAME),
            Token("|", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("number", LuaCatsTokenTypes.LCATS_NAME),
            Token("|", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(""""yes"""", LuaCatsTokenTypes.LCATS_STRING),
            Token("|", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(""""no"""", LuaCatsTokenTypes.LCATS_STRING),
        )

        testLexer(
            "boolean|'123'", TAG_TYPE,
            Token("boolean", LuaCatsTokenTypes.LCATS_NAME),
            Token("|", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("'123'", LuaCatsTokenTypes.LCATS_STRING),
        )

        testLexer(
            "fun(objectID: integer): boolean", TAG_TYPE,
            Token("fun", LuaCatsTokenTypes.LCATS_KEYWORD),
            Token("(", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("objectID", LuaCatsTokenTypes.LCATS_NAME),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("integer", LuaCatsTokenTypes.LCATS_NAME),
            Token(")", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(":", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("boolean", LuaCatsTokenTypes.LCATS_NAME),
        )

    }

    @Test
    fun testUnclosedBacktickDoesNotSpanNewline() {
        testLexer(
            "x `unclosed\n", TAG_PARAM,
            Token("x", LuaCatsTokenTypes.LCATS_NAME),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("`", LuaCatsTokenTypes.LCATS_TEXT),
            Token("unclosed", LuaCatsTokenTypes.LCATS_TEXT),
            Token("\n", LuaCatsTokenTypes.LCATS_WHITESPACE),
        )
    }

    @Test
    fun testUnclosedStringDoesNotSpanNewline() {
        testLexer(
            "\"unterminated\n", TAG_TYPE,
            Token("\"", LuaCatsTokenTypes.LCATS_TEXT),
            Token("unterminated", LuaCatsTokenTypes.LCATS_TEXT),
            Token("\n", LuaCatsTokenTypes.LCATS_WHITESPACE),
        )
    }

    @Test
    fun testUnicodeClassNameLexesAsSingleName() {
        testLexer(
            "名前", TAG_CLASS,
            Token("名前", LuaCatsTokenTypes.LCATS_NAME),
        )

        testLexer(
            "Игрок", TAG_CLASS,
            Token("Игрок", LuaCatsTokenTypes.LCATS_NAME),
        )
    }

    @Test
    fun testVersion() {
        testLexer(
            ">5.2, JIT", TAG_VERSION,
            Token(">", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token("5.2", LuaCatsTokenTypes.LCATS_STRING),
            Token(",", LuaCatsTokenTypes.LCATS_SYMBOL),
            Token(" ", LuaCatsTokenTypes.LCATS_WHITESPACE),
            Token("JIT", LuaCatsTokenTypes.LCATS_STRING),
        )

        testLexer(
            "5.4", TAG_VERSION,
            Token("5.4", LuaCatsTokenTypes.LCATS_STRING),
        )
    }

}