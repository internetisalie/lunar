package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment

open class LuaRecursiveVisitor : LuaVisitor() {
    private val visitedLuaCatsComments = mutableSetOf<LuaCatsComment>()

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        element.acceptChildren(this)
        
        // LuaCatsComment elements are lazy-parsed and might not be part of the normal
        // child traversal, so we need to visit them explicitly
        if (!visitedLuaCatsComments.contains(element) && element is LuaCatsComment) {
            visitedLuaCatsComments.add(element)
            element.acceptChildren(this)
        }
        
        // Also visit any LuaCatsComment children that haven't been visited yet
        val luaCatsComments = PsiTreeUtil.findChildrenOfType(element, LuaCatsComment::class.java)
        for (comment in luaCatsComments) {
            if (!visitedLuaCatsComments.contains(comment)) {
                visitedLuaCatsComments.add(comment)
                comment.accept(this)
            }
        }
    }
}