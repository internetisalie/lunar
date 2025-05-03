package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiElement

open class LuaRecursiveVisitor : LuaVisitor() {
    override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        element.acceptChildren(this)
    }
}