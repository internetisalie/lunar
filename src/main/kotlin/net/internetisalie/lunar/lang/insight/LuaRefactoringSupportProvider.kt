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
 * the Introduce Variable handler (REFACT-02). Safe delete (REFACT-03) is enabled via
 * [isSafeDeleteAvailable] — it delegates to [LuaFindUsagesProvider] so only declaration-site
 * identifiers (locals, parameters, function names, labels) are eligible.
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

    override fun isSafeDeleteAvailable(element: PsiElement): Boolean =
        LuaFindUsagesProvider().canFindUsagesFor(element)
}
