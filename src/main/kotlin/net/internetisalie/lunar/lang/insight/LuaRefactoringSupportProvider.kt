package net.internetisalie.lunar.lang.insight

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.refactoring.LuaIntroduceVariableHandler

/**
 * Consolidated [RefactoringSupportProvider] for Lua.
 *
 * Supersedes the label-only provider: it keeps in-place rename for labels (REFACT-04, which owns
 * label rename — not REFACT-01, whatever this KDoc said before) and adds the Introduce Variable
 * handler (REFACT-02). Safe delete (REFACT-03) is enabled via [isSafeDeleteAvailable], which asks
 * [LuaDeclarationSite] the same question Find Usages and reference search ask, so only declaration
 * sites (locals, parameters, function names, globals, labels) are eligible.
 */
class LuaRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isInplaceRenameAvailable(
        element: PsiElement,
        context: PsiElement,
    ): Boolean = false

    override fun isMemberInplaceRenameAvailable(
        elementToRename: PsiElement,
        context: PsiElement?,
    ): Boolean = elementToRename is LuaLabelName

    override fun getIntroduceVariableHandler(): RefactoringActionHandler = LuaIntroduceVariableHandler()

    override fun isSafeDeleteAvailable(element: PsiElement): Boolean = LuaDeclarationSite.kindOf(element) != null
}
