package net.internetisalie.lunar.lang.psi

import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocCommentOwner

interface LuaCommentOwner : LuaCatsCommentOwner, LuaDocCommentOwner {
    fun getComment() : LuaComment
}