package net.internetisalie.lunar.luacats.lang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

interface LuaCatsCommentOwner : PsiElement {
    val catsComment: LuaCatsComment?
}

open class LuaCatsBaseElement(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun toString(): String {
        return this.node.elementType.toString()
    }
}
