package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.LuaLabelName

/**
 * Refuses Rename for every Lua element except a `::label::` (BUG-457).
 *
 * Without this, rename *appears* to work and silently corrupts the file. The platform's own gate
 * (`PsiElementRenameHandler.getRenameErrorMessage`) admits any [PsiNamedElement], and
 * `LuaNameRefElement` is one — so the dialog opens, offers to rename "and its usages", renames the
 * declaration, and finds no usages, because `LuaNameReferenceSearcher` only collects raw IDENTIFIER
 * leaves. Measured live 2026-08-22: one occurrence renamed, four left bound to the old name, no
 * warning of any kind.
 *
 * This is deliberately a refusal and not a fix. Declining loudly is strictly better than a silent
 * partial rewrite, and it is a fraction of the work: the real fix needs a rename processor that
 * collects usages through `LuaNameReference`/`LuaScopeProcessor`, a registered `elementManipulator`,
 * and `findExistingNameConflicts` implemented against Lua's shadowing rules — which in Lua does not
 * collide but silently *rebinds*.
 *
 * **Labels are excluded on purpose.** `LuaLabelName` is the codebase's only `PsiNameIdentifierOwner`
 * and `LuaLabelReference` overrides `handleElementRename`, so label rename genuinely works
 * ([[REFACT-04]]); claiming it here would break the one refactoring that does.
 *
 * **`DumbAware` is load-bearing, not decoration.** `RenamePsiElementProcessorBase.forPsiElement`
 * skips any processor failing `dumbService.isUsableInCurrentContext` (`:156`), so without the
 * marker this refusal simply evaporates while the project is indexing and the rename falls
 * through to the platform default — reinstating the exact silent partial rewrite this class
 * exists to prevent, in a window the user cannot see. Both overrides are index-free (two `is`
 * tests and an error hint), so the marker is safe.
 *
 * Delete this class when a real processor lands.
 */
class LuaUnsupportedRenameProcessor :
    RenamePsiElementProcessor(),
    DumbAware {
    override fun canProcessElement(element: PsiElement): Boolean =
        element is PsiNamedElement &&
            element !is PsiFile &&
            element !is LuaLabelName &&
            element.language.isKindOf(LuaLanguage.INSTANCE)

    /**
     * Returning null aborts the rename before the dialog opens
     * (`RenamePsiElementProcessorBase.substituteElementToRename` discards a null substitution).
     */
    override fun substituteElementToRename(
        element: PsiElement,
        editor: Editor?,
    ): PsiElement? {
        CommonRefactoringUtil.showErrorHint(
            element.project,
            editor,
            RefactoringBundle.getCannotRefactorMessage(LuaBundle.message("refactoring.rename.unsupported")),
            RefactoringBundle.message("rename.title"),
            null,
        )
        return null
    }
}
