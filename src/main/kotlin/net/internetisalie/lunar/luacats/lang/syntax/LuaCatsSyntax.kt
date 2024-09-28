package net.internetisalie.lunar.luacats.lang.syntax

import com.intellij.psi.tree.TokenSet
import net.internetisalie.lunar.luadoc.lang.lexer.LuaDocTokenTypes

object LuaDocSyntax {
    val COMMENT_TAGS: TokenSet = TokenSet.create(
        LuaDocTokenTypes.LDOC_TAG_NAME
    )

    val COMMENT_CONTENT: TokenSet = TokenSet.create(
        LuaDocTokenTypes.LDOC_COMMENT_DATA, LuaDocTokenTypes.LDOC_DASHES, LuaDocTokenTypes.LDOC_COMMENT_START
    )

    val COMMENT_VALUES: TokenSet = TokenSet.create(
        LuaDocTokenTypes.LDOC_TAG_VALUE
    )
}