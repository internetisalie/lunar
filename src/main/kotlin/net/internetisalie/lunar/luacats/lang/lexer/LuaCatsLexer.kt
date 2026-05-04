package net.internetisalie.lunar.luacats.lang.lexer

import com.intellij.lexer.FlexAdapter
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes

class LuaCatsLexer : FlexAdapter(_LuaCatsLexer(null)) {
    val tokenTypes: Map<IElementType, IElementType> = mapOf(
        // LuaCatsTokenTypes.LCATS_WHITESPACE to LuaCatsElementTypes.WHITESPACE,
        LuaCatsTokenTypes.LCATS_DASHES to LuaCatsElementTypes.DASHES,
        LuaCatsTokenTypes.LCATS_TEXT to LuaCatsElementTypes.TEXT,
        LuaCatsTokenTypes.LCATS_NAME to LuaCatsElementTypes.NAME,
        LuaCatsTokenTypes.LCATS_STRING to LuaCatsElementTypes.STRING,
        LuaCatsTokenTypes.LCATS_SYMBOL to LuaCatsElementTypes.SYMBOL,
        LuaCatsTokenTypes.LCATS_TAG to LuaCatsElementTypes.TAG,
        LuaCatsTokenTypes.LCATS_KEYWORD to LuaCatsElementTypes.KEYWORD,
        LuaCatsTokenTypes.LCATS_CODE to LuaCatsElementTypes.CODE,
        LuaCatsTokenTypes.LCATS_NUMBER to LuaCatsElementTypes.NUMBER,
    );

    val tokenSet : TokenSet = TokenSet.create(
        // LuaCatsElementTypes.WHITESPACE,
        LuaCatsElementTypes.DASHES,
        LuaCatsElementTypes.TEXT,
        LuaCatsElementTypes.NAME,
        LuaCatsElementTypes.STRING,
        LuaCatsElementTypes.SYMBOL,
        LuaCatsElementTypes.TAG,
        LuaCatsElementTypes.KEYWORD,
        LuaCatsElementTypes.CODE,
        LuaCatsElementTypes.NUMBER,
    )

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
