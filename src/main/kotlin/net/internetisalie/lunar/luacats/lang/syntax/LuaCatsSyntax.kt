package net.internetisalie.lunar.luacats.lang.syntax

import com.intellij.psi.tree.TokenSet
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes

object LuaCatsSyntax {
    val CONTENT: TokenSet = TokenSet.create(
        LuaCatsElementTypes.DASHES,
        LuaCatsElementTypes.TEXT,
    )

    val NAMES: TokenSet = TokenSet.create(
        LuaCatsElementTypes.NAME,
    )
    
    val SYMBOLS: TokenSet = TokenSet.create(
        LuaCatsElementTypes.SYMBOL,
    )

    val TAGS: TokenSet = TokenSet.create(
        LuaCatsElementTypes.TAG
    )

}