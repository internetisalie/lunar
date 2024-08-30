package net.internetisalie.lunar.lang

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaLabelName

class LuaLabelRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement): Boolean {
        return false; }

    override fun isMemberInplaceRenameAvailable(elementToRename: PsiElement, context: PsiElement?): Boolean {
        return (elementToRename is LuaLabelName)
    }
}
