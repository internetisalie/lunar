package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.LuaCatsTypeReference
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations

/**
 * Rename for a LuaCATS `@class`/`@alias` type name (`REFACT-08` design.md §2.6, §3.6, §3.7, §3.9,
 * §3.11).
 *
 * **Superclass is `RenamePsiElementProcessor`, not `RenamePsiElementProcessorBase`, for
 * [LuaLabelRenameProcessor]'s reason**: `RenamePsiElementProcessor.forElement` casts the extension
 * instance unconditionally. **`DumbAware` is not decoration** either, for the same reason: both
 * `forElement` and `forPsiElement` skip a processor `DumbService.isUsableInCurrentContext` refuses,
 * while `RenameElementAction` stays a `DumbAwareAction`. Safe here because every index-backed step —
 * [substituteElementToRename]'s resolve, [findReferences]'s search — runs before the first write, so
 * an unavailable index ends the refactoring as a refusal, never as a half-applied rename.
 *
 * [LuaRenameProcessor] and this processor are disjoint by construction (design §1.3):
 * `LuaRenameProcessor.canProcessElement` requires an `IDENTIFIER` node or a `LuaNameRef`, and a
 * LuaCATS name leaf is `LuaCatsElementTypes.NAME`, which is neither.
 */
class LuaCatsTypeRenameProcessor :
    RenamePsiElementProcessor(),
    DumbAware {
    /**
     * Design §3.6 — admits both a NAME leaf and its use holder, because a caret can land on either;
     * [substituteElementToRename] normalises whichever arrives.
     */
    override fun canProcessElement(element: PsiElement): Boolean =
        LuaCatsTypeDeclarations.isDeclarationLeaf(element) ||
            LuaCatsTypeDeclarations.useHolderOf(element) != null ||
            LuaCatsTypeDeclarations.useLeafOf(element) != null

    /**
     * Design §3.6. A declaration caret is refused when it names a builtin keyword
     * (`REFACT-08-07`) or when any of its declaration slots lies outside the project
     * (`REFACT-08-16`, design §3.11) — both refusals run before any write and before the rename
     * dialog, so a refusal costs no write and no user input. A use caret substitutes to its
     * resolved declaration, or is refused when nothing resolves (a parameterized class head has no
     * holder at all and never reaches this method — design §3.6 step 8's note).
     */
    override fun substituteElementToRename(
        element: PsiElement,
        editor: Editor?,
    ): PsiElement? {
        if (LuaCatsTypeDeclarations.isDeclarationLeaf(element)) {
            return substituteDeclaration(element, editor)
        }
        val holder =
            if (LuaCatsTypeDeclarations.useLeafOf(element) !=
                null
            ) {
                element
            } else {
                LuaCatsTypeDeclarations.useHolderOf(element)
            }
        holder ?: return null
        val resolved = (holder.reference as? LuaCatsTypeReference)?.resolve()
        return resolved
            ?: refuse(holder, editor, LuaBundle.message("refactoring.rename.catsUnresolvedType", holder.text))
    }

    private fun substituteDeclaration(
        element: PsiElement,
        editor: Editor?,
    ): PsiElement? {
        if (element.text in LuaCatsTypeDeclarations.BUILTIN_KEYWORDS) {
            return refuse(element, editor, LuaBundle.message("refactoring.rename.catsBuiltinType", element.text))
        }
        val outside = LuaCatsTypeDeclarations.outOfProjectDeclarationFiles(element.text, element.project)
        if (outside.isNotEmpty()) {
            return refuse(
                element,
                editor,
                LuaBundle.message("refactoring.rename.catsLibraryType", element.text, outside.first()),
            )
        }
        return element
    }

    /** Design §3.7 — every use holder [element]'s reference already resolves through the index. */
    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean,
    ): Collection<PsiReference> = ReferencesSearch.search(element, searchScope).findAll()

    /**
     * Design §3.7. Runs inside the platform's own write action (`BaseRefactoringProcessor`
     * documents `renameElement` as called "in a command, on EDT, inside a Write Action"), so it
     * opens no `WriteCommandAction` of its own.
     *
     * **The declaration set is read here, over [GlobalSearchScope.projectScope] — not the
     * `allScope` [LuaCatsTypeReference] resolves through.** [substituteElementToRename] has already
     * refused any name whose declarations differ between the two scopes, so at this point the two
     * sets are equal; the narrower scope is what makes that an invariant of this code rather than of
     * the caller (design §3.11).
     *
     * Every rewrite is resolved into a closure before the first one runs, and every closure runs
     * inside one [ProgressManager.executeNonCancelableSection] — usages first, then declarations,
     * matching `RenameUtilBase.doRenameGenericNamedElement`'s own order so no usage's reference is
     * invalidated by a declaration edit. A cancellation between the two halves would otherwise be a
     * silent half-apply (design §3.7, REFACT-01 design §3.3's rule).
     */
    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<UsageInfo>,
        listener: RefactoringElementListener?,
    ) {
        val declarations =
            LuaCatsTypeDeclarations.declarationLeaves(
                element.text,
                element.project,
                GlobalSearchScope.projectScope(element.project),
            )
        val usageRewrites = preparedUsageRewrites(usages, newName)
        val declarationRewrites = preparedDeclarationRewrites(declarations, newName)
        ProgressManager.getInstance().executeNonCancelableSection {
            usageRewrites.forEach { applyRewrite -> applyRewrite() }
            declarationRewrites.forEach { applyRewrite -> applyRewrite() }
        }
        listener?.elementRenamed(element)
    }

    private fun preparedUsageRewrites(
        usages: Array<UsageInfo>,
        newName: String,
    ): List<() -> Unit> =
        usages.mapNotNull { usage ->
            ProgressManager.checkCanceled()
            val reference = usage.reference ?: usage.element?.reference
            reference?.let { { it.handleElementRename(newName) } }
        }

    private fun preparedDeclarationRewrites(
        declarations: List<PsiElement>,
        newName: String,
    ): List<() -> Unit> =
        declarations.mapNotNull { leaf ->
            (leaf.node as? LeafElement)?.let { node -> { node.replaceWithText(newName) } }
        }

    /**
     * Design §3.9 — disables the non-code search route rather than running it against garbage: the
     * base hook would hand `ElementDescriptionUtil` a bare `LeafPsiElement` and end at its debug
     * string, and a text pass over comments would rewrite the prose mentions this feature
     * deliberately excludes.
     */
    override fun getElementToSearchInStringsAndComments(element: PsiElement): PsiElement? = null

    /**
     * Design §3.9 — the base hook returns null for a non-`PsiNamedElement`, and the null reaches
     * `document.replaceString` as replacement text. [newName] is what the `PsiNamedElement` branch
     * would have returned.
     */
    override fun getQualifiedNameAfterRename(
        element: PsiElement,
        newName: String,
        nonJava: Boolean,
    ): String = newName

    /**
     * Design §2.9, §3.10 (`REFACT-08-11`). A no-op for a use caret — [element] here is always the
     * substituted declaration leaf, since [substituteElementToRename] already resolved a use to its
     * declaration before the platform ever asks for collisions.
     *
     * **The anchor is the rival declaration, not a usage** — the element the user must look at,
     * following [LuaLabelConflictDetector]'s stated rule. Without this, two `@class`/`@alias`
     * declarations of the same name silently merge: `LuaTypeManagerImpl.materializeClass` merges
     * the members of every tag sharing a name, so the user would get one type with the union of
     * both declarations' members and no warning at all.
     *
     * Reuses the existing [LuaRenameCollisionUsageInfo] carrier (design §2.9) — no second carrier is
     * defined. Runs inside `BaseRefactoringProcessor`'s background read action, never the EDT, the
     * same placement [LuaRenameProcessor.findCollisions] documents.
     */
    override fun findCollisions(
        element: PsiElement,
        newName: String,
        allRenames: Map<out PsiElement, String>,
        result: MutableList<UsageInfo>,
    ) {
        if (!LuaCatsTypeDeclarations.isDeclarationLeaf(element)) return
        val allScope = GlobalSearchScope.allScope(element.project)
        val rivals = LuaCatsTypeDeclarations.declarationLeaves(newName, element.project, allScope)
        rivals.forEach { rival ->
            result +=
                LuaRenameCollisionUsageInfo(
                    rival,
                    element,
                    LuaBundle.message("refactoring.rename.conflict.catsTypeExists", newName),
                )
        }
    }

    /** Always null, so a caller can `return refuse(...)`: refusing IS returning no target. */
    private fun refuse(
        anchor: PsiElement,
        editor: Editor?,
        message: String,
    ): PsiElement? {
        CommonRefactoringUtil.showErrorHint(
            anchor.project,
            editor,
            RefactoringBundle.getCannotRefactorMessage(message),
            RefactoringBundle.message("rename.title"),
            null,
        )
        return null
    }
}
