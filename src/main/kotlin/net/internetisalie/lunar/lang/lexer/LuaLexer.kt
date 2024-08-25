package net.internetisalie.lunar.lang.lexer

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.MergingLexerAdapter
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import net.internetisalie.lunar.lang.psi.LuaElementTypes

class LuaLexer : MergingLexerAdapter(
    FlexAdapter(_LuaLexer(null)),
    TokenSet.create(
        LuaTokenTypes.LONGCOMMENT,
        LuaTokenTypes.LONGSTRING,
        LuaTokenTypes.STRING,
        LuaTokenTypes.SHORTCOMMENT
    )
) {
    val tokenTypes: Map<IElementType, IElementType> = mapOf(
        LuaTokenTypes.AMP to LuaElementTypes.AMP,
        LuaTokenTypes.AND to LuaElementTypes.AND,
        LuaTokenTypes.ASSIGN to LuaElementTypes.ASSIGN,
        LuaTokenTypes.BREAK to LuaElementTypes.BREAK,
        LuaTokenTypes.BSL to LuaElementTypes.BSL,
        LuaTokenTypes.BSR to LuaElementTypes.BSR,
        LuaTokenTypes.COLON to LuaElementTypes.COLON,
        LuaTokenTypes.COMMA to LuaElementTypes.COMMA,
        LuaTokenTypes.CONCAT to LuaElementTypes.CONCAT,
        LuaTokenTypes.DIV to LuaElementTypes.DIV,
        LuaTokenTypes.DO to LuaElementTypes.DO,
        LuaTokenTypes.DOT to LuaElementTypes.DOT,
        LuaTokenTypes.ELLIPSIS to LuaElementTypes.ELLIPSIS,
        LuaTokenTypes.ELSE to LuaElementTypes.ELSE,
        LuaTokenTypes.ELSEIF to LuaElementTypes.ELSEIF,
        LuaTokenTypes.END to LuaElementTypes.END,
        LuaTokenTypes.EQ to LuaElementTypes.EQ,
        LuaTokenTypes.EXP to LuaElementTypes.EXP,
        LuaTokenTypes.FALSE to LuaElementTypes.FALSE,
        LuaTokenTypes.FOR to LuaElementTypes.FOR,
        LuaTokenTypes.FUNCTION to LuaElementTypes.FUNCTION,
        LuaTokenTypes.GE to LuaElementTypes.GE,
        LuaTokenTypes.GETN to LuaElementTypes.GETN,
        LuaTokenTypes.GOTO to LuaElementTypes.GOTO,
        LuaTokenTypes.GT to LuaElementTypes.GT,
        LuaTokenTypes.IF to LuaElementTypes.IF,
        LuaTokenTypes.IN to LuaElementTypes.IN,
        LuaTokenTypes.INTDIV to LuaElementTypes.INTDIV,
        LuaTokenTypes.LBRACK to LuaElementTypes.LBRACK,
        LuaTokenTypes.LCURLY to LuaElementTypes.LCURLY,
        LuaTokenTypes.LE to LuaElementTypes.LE,
        LuaTokenTypes.LOCAL to LuaElementTypes.LOCAL,
        LuaTokenTypes.LPAREN to LuaElementTypes.LPAREN,
        LuaTokenTypes.LT to LuaElementTypes.LT,
        LuaTokenTypes.MINUS to LuaElementTypes.MINUS,
        LuaTokenTypes.MOD to LuaElementTypes.MOD,
        LuaTokenTypes.MULT to LuaElementTypes.MULT,
        LuaTokenTypes.IDENTIFIER to LuaElementTypes.IDENTIFIER,
        LuaTokenTypes.NE to LuaElementTypes.NE,
        LuaTokenTypes.NEG to LuaElementTypes.NEG,
        LuaTokenTypes.NIL to LuaElementTypes.NIL,
        LuaTokenTypes.NOT to LuaElementTypes.NOT,
        LuaTokenTypes.NUMBER to LuaElementTypes.NUMBER,
        LuaTokenTypes.OR to LuaElementTypes.OR,
        LuaTokenTypes.PIPE to LuaElementTypes.PIPE,
        LuaTokenTypes.PLUS to LuaElementTypes.PLUS,
        LuaTokenTypes.RBRACK to LuaElementTypes.RBRACK,
        LuaTokenTypes.RCURLY to LuaElementTypes.RCURLY,
        LuaTokenTypes.REPEAT to LuaElementTypes.REPEAT,
        LuaTokenTypes.RETURN to LuaElementTypes.RETURN,
        LuaTokenTypes.RPAREN to LuaElementTypes.RPAREN,
        LuaTokenTypes.SEMI to LuaElementTypes.SEMI,
        LuaTokenTypes.SHEBANG to LuaElementTypes.SHEBANG,
        LuaTokenTypes.SHORTCOMMENT to LuaElementTypes.SHORTCOMMENT,
        LuaTokenTypes.STRING to LuaElementTypes.STRING,
        LuaTokenTypes.THEN to LuaElementTypes.THEN,
        LuaTokenTypes.TRUE to LuaElementTypes.TRUE,
        LuaTokenTypes.UNTIL to LuaElementTypes.UNTIL,
        LuaTokenTypes.WHILE to LuaElementTypes.WHILE,
    );

    override fun getTokenType(): IElementType? {
        val sourceType = super.getTokenType()
        if (sourceType != null) {
            val targetType = tokenTypes[sourceType]
            if (targetType != null) {
                return targetType
            }
        }
        return super.getTokenType()
    }
}
