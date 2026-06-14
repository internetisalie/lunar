package net.internetisalie.lunar.lang.insight

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.refactoring.LuaIntroduceVariableHandler

/**
 * Consolidated [RefactoringSupportProvider] for Lua.
 *
 * Supersedes the label-only provider: it keeps in-place rename for labels (REFACT-01) and adds
 * the Introduce Variable handler (REFACT-02). The safe-delete hook (REFACT-03) is intentionally
 * left at the default until that feature lands.
 */
class LuaRefactoringSupportProvider : RefactoringSupportProvider() {

    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement): Boolean {
        return false
    }

    override fun isMemberInplaceRenameAvailable(elementToRename: PsiElement, context: PsiElement?): Boolean {
        return elementToRename is LuaLabelName
    }

    override fun getIntroduceVariableHandler(): RefactoringActionHandler {
        return LuaIntroduceVariableHandler()
    }
}
