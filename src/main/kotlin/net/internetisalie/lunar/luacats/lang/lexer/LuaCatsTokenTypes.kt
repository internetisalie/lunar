package net.internetisalie.lunar.luacats.lang.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

object LuaCatsTokenTypes {
    @JvmField val LCATS_DASHES: IElementType = LuaCatsElementType("LCATS_DASHES")
    @JvmField val LCATS_WHITESPACE: IElementType = LuaCatsElementType("LCATS_WHITESPACE")
    @JvmField val LCATS_TEXT: IElementType = LuaCatsElementType("LCATS_TEXT")

    @JvmField val LCATS_NAME: IElementType = LuaCatsElementType("LCATS_NAME")
    @JvmField val LCATS_STRING: IElementType = LuaCatsElementType("LCATS_STRING")
    @JvmField val LCATS_SYMBOL: IElementType = LuaCatsElementType("LCATS_SYMBOL")
    @JvmField val LCATS_TAG: IElementType = LuaCatsElementType("LCATS_TAG")
    @JvmField val LCATS_KEYWORD: IElementType = LuaCatsElementType("LCATS_KEYWORD")
    @JvmField val LCATS_CODE: IElementType = LuaCatsElementType("LCATS_CODE")
    @JvmField val LCATS_NUMBER: IElementType = LuaCatsElementType("LCATS_NUMBER")

    @JvmField val LCATS_BAD_CHARACTER: IElementType = TokenType.BAD_CHARACTER
}
