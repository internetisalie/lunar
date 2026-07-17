package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiElement
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment

open class LuaRecursiveVisitor : LuaVisitor() {
    private val visitedLuaCatsComments = mutableSetOf<LuaCatsComment>()

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        element.acceptChildren(this)

        // LuaCatsComment elements are lazy-parsed and might not be part of the normal
        // child traversal, so we visit them explicitly.
        if (element is LuaCatsComment && visitedLuaCatsComments.add(element)) {
            element.acceptChildren(this)
        }

        // Every LuaCatsComment attaches as a DIRECT child of some visited PSI container (verified by
        // MAINT-25-00-DR-02: comments parent to LuaFile/LuaBlock/LuaFuncDecl/LuaDoStatement, never
        // nested more than one level below), so a direct-child scan at each node collectively
        // reaches all of them without the per-element deep findChildrenOfType (which was O(n²)).
        for (child in element.children) {
            if (child is LuaCatsComment && visitedLuaCatsComments.add(child)) {
                child.accept(this)
            }
        }
    }
}