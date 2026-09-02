package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.LuaLabelReference
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLabelRef

/**
 * Attaches Lua's label rules to the rename the platform already performs (design §2.2). It adds a
 * conflict check and a refusal; it performs no rename of its own.
 *
 * **`label rename works today; this must not disturb it`** (design §1). Only [canProcessElement],
 * [substituteElementToRename] and [findCollisions] are overridden; every other hook — including
 * [renameElement] and `findReferences` — is inherited from `RenamePsiElementProcessorBase`, which
 * is the same code the label rename runs through today. Design §3.6 enumerates each inherited hook
 * and why the omission is deliberate; adding either of those two would replace the one rename in
 * this plugin with a passing end-to-end test.
 *
 * **Superclass is `RenamePsiElementProcessor`, not `RenamePsiElementProcessorBase`.**
 * `MemberInplaceRenamer.MyRenameProcessor` calls `RenamePsiElementProcessor.forElement(element)`,
 * which casts the EP instance unconditionally — the `…Base` type is a `ClassCastException` in the
 * IDE on every in-place label rename, a path no headless test can reach (design §1, DR-02).
 *
 * **`DumbAware` is load-bearing, not decoration.** Both `RenamePsiElementProcessorBase.forPsiElement`
 * and `RenamePsiElementProcessor.forElement` skip a processor for which `DumbService
 * .isUsableInCurrentContext` is false, while `RenameElementAction` stays a `DumbAwareAction` — so
 * without the marker the conflict check silently disappears while the project is indexing, with no
 * other symptom. Safe because all three overrides are index-free (design §2.2): two `is` tests here,
 * and [LuaLabelConflictDetector] resolving nothing and reading no index.
 */
class LuaLabelRenameProcessor :
    RenamePsiElementProcessor(),
    DumbAware {
    /** Design §3.0 — two disjoint type tests, nothing else. Registration order does not matter. */
    override fun canProcessElement(element: PsiElement): Boolean = element is LuaLabelName || element is LuaLabelRef

    /**
     * Design §3.1. A [LuaLabelName] is returned unchanged — the base class's own behaviour, restated
     * so the label declaration path is provably untouched. A [LuaLabelRef] (the `goto` side)
     * substitutes to the label it resolves to, or the rename is refused with a reason: renaming an
     * unresolved `goto` in place would bind it to a label it did not previously refer to.
     */
    override fun substituteElementToRename(
        element: PsiElement,
        editor: Editor?,
    ): PsiElement? {
        if (element is LuaLabelName) return element
        val resolved = (element.reference as? LuaLabelReference)?.resolve()
        if (resolved is LuaLabelName) return resolved
        CommonRefactoringUtil.showErrorHint(
            element.project,
            editor,
            RefactoringBundle.getCannotRefactorMessage(
                LuaBundle.message("refactoring.rename.label.unresolvedGoto", element.text),
            ),
            RefactoringBundle.message("rename.title"),
            null,
        )
        return null
    }

    /** Design §3.3 — delegates the whole body to [LuaLabelConflictDetector]. */
    override fun findCollisions(
        element: PsiElement,
        newName: String,
        allRenames: Map<out PsiElement, String>,
        result: MutableList<UsageInfo>,
    ) {
        val label = element as? LuaLabelName ?: return
        result.addAll(LuaLabelConflictDetector.collisions(LuaLabelRenameTarget(label, newName)))
    }
}
