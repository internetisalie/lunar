package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes

class LuaPairedBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> {
        return arrayOf(
            BracePair(LuaElementTypes.LPAREN, LuaElementTypes.RPAREN, false),
            BracePair(LuaElementTypes.LBRACK, LuaElementTypes.RBRACK, false),
            BracePair(LuaElementTypes.LCURLY, LuaElementTypes.RCURLY, false)
        )
    }

    override fun isPairedBracesAllowedBeforeType(
        lbraceType: IElementType,
        contextType: IElementType?
    ): Boolean {
        return true
    }

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int {
        return openingBraceOffset
    }
}
