package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.LuaDeclarationKind
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLabelRef
import net.internetisalie.lunar.lang.psi.LuaNameRef

/**
 * Rename for Lua identifiers — locals, parameters, `for` variables, local functions and globals
 * (REFACT-01, design §2.2/§3.0-§3.3). Replaces `LuaUnsupportedRenameProcessor`, whose blanket
 * refusal was the interim fix for BUG-457.
 *
 * Lunar models no declaration PSI: apart from `::labels::` every declared name is a [LuaNameRef]
 * in a particular parent container, so the whole processor is keyed on the declaration IDENTIFIER
 * **leaf** that [LuaDeclarationSite] normalises to — the same key reference search, Find Usages
 * and Safe Delete use.
 *
 * **Where a correct rewrite does not exist, this refuses.** A rename that half-applies is BUG-457
 * verbatim — measured live: the declaration rewritten, four usages left bound to the old name, no
 * warning of any kind — and it is strictly worse than declining. Three refusals are decided in
 * [substituteElementToRename], before the refactoring starts: an unresolvable usage, a
 * `function Obj:method()` declaration and a function-name receiver segment. A fourth is decided in
 * [renameElement], where the cost of being wrong is highest — a new name with no identifier PSI is
 * refused outright rather than applied to the usages and abandoned at the declaration. Each names
 * its reason rather than aborting silently.
 *
 * **`DumbAware` is load-bearing, not decoration**, and it is inherited from the class this replaces.
 * `RenamePsiElementProcessorBase.forPsiElement` skips any processor failing
 * `dumbService.isUsableInCurrentContext` (`:156`) while `RenameElementAction` is a
 * `DumbAwareAction` (`RenameElementAction.java:35`) — so without the marker rename during indexing
 * falls through to the platform default, whose `RenameUtilBase.doRenameGenericNamedElement`
 * rewrites the `LuaNameRef` through `setName` and collects no usages. That is BUG-457 again, in a
 * window the user cannot see. Claiming the element instead is safe in both directions: the
 * index-backed steps are [substituteElementToRename]'s resolve and [findReferences]' search, both
 * of which run before any write, so an index that is unavailable during indexing ends the
 * refactoring either as the platform's "not available while indexing" report or as this
 * processor's own `refactoring.rename.unresolved` refusal — never as a half-applied rename.
 */
class LuaRenameProcessor :
    RenamePsiElementProcessor(),
    DumbAware {
    /**
     * Design §3.0, in this order — every clause is load-bearing.
     *
     * Labels are excluded FIRST and must not be folded into the `kindOf` test:
     * `kindOf(LuaLabelName)` is [LuaDeclarationKind.LABEL], not null, and `forPsiElement` returns
     * the FIRST matching extension, so claiming them would take the one refactoring that works
     * today (REFACT-04) away from the platform default that makes it work. [LuaLabelRef] goes with
     * it — it is the `goto` half of the same pair and is not a [LuaNameRef], so the last clause
     * would not exclude it.
     *
     * Every Lua [LuaNameRef] is claimed, usage as well as declaration. That is deliberate
     * over-claiming: an unresolvable usage left to the platform default is renamed in place with
     * no usages collected, so claiming it is what lets [substituteElementToRename] refuse it with
     * a reason.
     *
     * No resolution and no index read here — it is called from
     * `RenameHandler.isAvailableOnDataContext`.
     */
    override fun canProcessElement(element: PsiElement): Boolean {
        if (element is LuaLabelName || element is LuaLabelRef) return false
        if (element is PsiFileSystemItem) return false
        if (!element.language.isKindOf(LuaLanguage.INSTANCE)) return false
        return element is LuaNameRef || LuaDeclarationSite.kindOf(element) != null
    }

    /**
     * Design §3.1 — normalise whatever the caret produced to the declaration IDENTIFIER leaf, or
     * refuse.
     *
     * Refusal is an error hint plus `null`. Headlessly `CommonRefactoringUtil.showErrorHint`
     * throws [CommonRefactoringUtil.RefactoringErrorHintException] instead of painting a balloon,
     * so under `BasePlatformTestCase` this never reaches its own `return`.
     */
    override fun substituteElementToRename(
        element: PsiElement,
        editor: Editor?,
    ): PsiElement? {
        val leaf =
            LuaDeclarationSite.identifierLeafOf(element)
                ?: resolvedDeclarationLeaf(element, editor)
                ?: return null
        receiverSegmentRefusal(leaf)?.let { return refuse(leaf, editor, it) }
        return when (LuaDeclarationSite.kindOf(leaf)) {
            LuaDeclarationKind.METHOD_FUNCTION ->
                refuse(leaf, editor, LuaBundle.message("refactoring.rename.colonMethod"))
            // Unreachable — canProcessElement excludes labels — but the invariant stays local.
            LuaDeclarationKind.LABEL -> null
            else -> leaf
        }
    }

    /**
     * Design §3.2 — a file-local kind can have no cross-file usage by Lua's scoping rules, so
     * narrowing the scope changes cost, not results. This override is the only reliable place to
     * do it: `RenameUtil.processUsages` uses the scope it is handed verbatim.
     */
    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean,
    ): Collection<PsiReference> {
        val kind =
            LuaDeclarationSite.kindOf(element)
                ?: return super.findReferences(element, searchScope, searchInCommentsAndStrings)
        val effectiveScope = if (kind.isFileLocal) LocalSearchScope(element.containingFile) else searchScope
        return ReferencesSearch.search(element, effectiveScope).findAll()
    }

    /**
     * Design §3.4 — a Lua rename does not collide, it silently **rebinds**, so the four rules of
     * [LuaRenameConflictDetector] are the difference between "rename works" and "rename tells you
     * when it will change meaning".
     *
     * `RenameUtil.findUsages` hands this the very list `processUsages` just filled
     * (`RenameUtil.java:97-103`), so [result] is **snapshotted** before anything is appended: the
     * detector reads the usages as C1's sites, and appending while reading would feed it the
     * collisions it is producing.
     *
     * Runs inside `BaseRefactoringProcessor`'s background read action, never the EDT — which is
     * why conflict detection lives here and not in `findExistingNameConflicts`, whose hook the
     * requirement names but which the platform calls from `preprocessUsages` on the EDT.
     *
     * An element that names no Lua declaration site yields no collisions, which is a statement
     * about Lua rather than a dropped result: every rule below is defined over a declaration and
     * its visibility, and there is neither for an element [LuaDeclarationSite] does not recognise.
     * [substituteElementToRename] has already refused anything that would reach here in that
     * state.
     */
    override fun findCollisions(
        element: PsiElement,
        newName: String,
        allRenames: Map<out PsiElement, String>,
        result: MutableList<UsageInfo>,
    ) {
        val declarationLeaf = LuaDeclarationSite.identifierLeafOf(element) ?: return
        val kind = LuaDeclarationSite.kindOf(declarationLeaf) ?: return
        val target = LuaRenameTarget(declarationLeaf, kind, newName)
        result.addAll(LuaRenameConflictDetector.collisions(target, result.toList()))
    }

    /**
     * Design §3.3 — runs inside the platform's own write action (`BaseRefactoringProcessor`
     * documents `renameElement` as called "in a command, on EDT, inside a Write Action"), so it
     * opens no `WriteCommandAction` of its own.
     *
     * **Everything that can fail is resolved BEFORE the first edit, and a failure refuses the whole
     * rename.** This method mutates two independent places — every usage, then the declaration leaf
     * — and an early `return` between them would leave every usage on the new name with the
     * declaration still on the old one: BUG-457 inverted, silently, inside the method written to
     * eliminate BUG-457. Both halves rewrite through [LuaElementFactory.createIdentifier] with the
     * same name and project (usages via `LuaNameReference.handleElementRename` → `LuaNameRef.setName`,
     * `LuaBaseElements.kt:83-92`), so building the replacement once up front decides both: if it
     * builds here it builds for every usage, and if it does not, nothing is written at all. An
     * [IncorrectOperationException] is the platform's own channel for that verdict —
     * `RenameProcessor.performRefactoring` catches it and reports it through
     * `RenameUtil.showErrorMessage`. TC-36, mutation-proved against the previous ordering.
     *
     * Usages are still rewritten BEFORE the declaration, matching
     * `RenameUtilBase.doRenameGenericNamedElement`'s own order, so no usage's reference is
     * invalidated by the declaration edit.
     *
     * **Step 5's `---@param` propagation runs last and is safe there** — `risks-and-gaps.md`
     * Gap 2.13 names it as the first rewrite path that could restore a visible half-apply, since it
     * edits comment text and so is not covered by the replacement resolved up front.
     * [LuaCatsParamRenamer] answers that by having no failure outcome at all (its KDoc states the
     * measurement); every way out of it short of the rewrite means there is no matching tag to
     * move, which is a correct no-op. It therefore cannot abort a rename that is already applied,
     * and TC-20d pins the other direction: a REFUSED rename never reaches it, so the tag does not
     * move on its own.
     *
     * [LuaCatsParamRenamer] re-derives the comment owner from [replacement] instead of from a value
     * captured in step 1, which design §3.3 has it do twice. Measured: `replaceChild` puts the
     * replacement in the old leaf's slot, so the ancestor chain — and the owner it reaches — is
     * unchanged by the swap. A pre-captured owner would only restate the renamer's own first step.
     */
    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<UsageInfo>,
        listener: RefactoringElementListener?,
    ) {
        val declarationKind = LuaDeclarationSite.kindOf(element)
        val oldName = element.text
        val replacement =
            LuaElementFactory.createIdentifier(element.project, newName) ?: refuseRewrite(newName)
        val applyDeclarationRewrite = preparedDeclarationRewrite(element, replacement) ?: refuseRewrite(newName)
        usages.forEach { usage ->
            ProgressManager.checkCanceled()
            RenameUtil.rename(usage, newName)
        }
        applyDeclarationRewrite()
        if (declarationKind == LuaDeclarationKind.PARAMETER) {
            LuaCatsParamRenamer.rename(replacement, oldName, newName)
        }
        listener?.elementRenamed(replacement)
    }

    /**
     * Design §2.9 — the string the platform SUBSTITUTES in a non-code occurrence, derived from the
     * renamed element rather than from [getElementToSearchInStringsAndComments].
     *
     * This ships with the processor rather than with §2.9's other five accessors because
     * `RenameDialog.createCheckboxes` adds "Search in comments and strings" unconditionally
     * (`RenameDialog.java:279-282`) and passes `isSearchInComments()` into `RenameProcessor`
     * (`:405`). One click therefore reaches `RenameUtil.getStringToReplace` with the renamed
     * **leaf**, which is not a `PsiNamedElement`; the base hook returns null
     * (`RenamePsiElementProcessorBase.java:106-108`), `RenameUtil.java:226` logs
     * `"Unknown element type : "` and the null reaches `document.replaceString` as the replacement
     * text. Returning [newName] is what the `PsiNamedElement` branch would have returned.
     */
    override fun getQualifiedNameAfterRename(
        element: PsiElement,
        newName: String,
        nonJava: Boolean,
    ): String = newName

    /** Design §3.1 steps 2-3 — a usage redirects to its declaration, or is refused with a reason. */
    private fun resolvedDeclarationLeaf(
        element: PsiElement,
        editor: Editor?,
    ): PsiElement? {
        val reference = element.reference ?: (element.parent as? LuaNameRef)?.reference
        val resolved =
            reference?.resolve()
                ?: return refuse(element, editor, LuaBundle.message("refactoring.rename.unresolved"))
        return LuaDeclarationSite.identifierLeafOf(resolved)
            ?: refuse(element, editor, LuaBundle.message("refactoring.rename.unsupportedTarget"))
    }

    /**
     * Design §3.1 step 4a — the receiver segment of a function name declares nothing renameable.
     *
     * In `function M.run() end` the `M` leaf is classified `GLOBAL_FUNCTION` by §3.5 row 9, so
     * without this guard rename lands here and half-applies: `LuaBlock.processDeclarations` has no
     * `LuaFuncDecl` branch (`LuaBlockExt.kt:38-77`) and the declaration is stub-indexed under
     * `"M.run"`, so `M` resolves to nothing, every `M.run()` call site's `isReferenceTo` is false,
     * and the declaration alone is rewritten.
     *
     * Written as a ROUND TRIP against [LuaDeclarationSite.functionNameLeafOf] rather than as a
     * shape enumeration, so it admits exactly the leaf the classifier calls the function's own
     * name and cannot fall behind it — which is also what makes it refuse the intermediate `B` of
     * `function A.B.run() end` while leaving `run` renameable. `globalFuncDecl` has no
     * `LuaFuncName` node at all (`lua.bnf:229`), so this is inert for `global function f() end`.
     */
    private fun receiverSegmentRefusal(leaf: PsiElement): String? {
        val funcName = PsiTreeUtil.getParentOfType(leaf, LuaFuncName::class.java, /* strict = */ false) ?: return null
        if (LuaDeclarationSite.functionNameLeafOf(funcName) === leaf) return null
        return LuaBundle.message("refactoring.rename.functionNameSegment", leaf.text)
    }

    /**
     * The declaration's AST swap, fully resolved but NOT yet applied — or `null` if it cannot be
     * expressed, which is the whole point: every node this edit needs is read before
     * [renameElement] writes anything, so the swap can no longer fail halfway through a rename
     * whose usages are already rewritten.
     */
    private fun preparedDeclarationRewrite(
        element: PsiElement,
        replacement: PsiElement,
    ): (() -> Unit)? {
        val parentNode = element.parent?.node ?: return null
        val targetNode = element.node ?: return null
        val replacementNode = replacement.node ?: return null
        return { parentNode.replaceChild(targetNode, replacementNode) }
    }

    /**
     * Refuses an already-approved rename that cannot be applied. Returns [Nothing], so it reads as
     * an Elvis fallback while remaining a refusal: the alternative — returning early — is the
     * half-applied rename this processor exists to make impossible.
     */
    private fun refuseRewrite(newName: String): Nothing =
        throw IncorrectOperationException(LuaBundle.message("refactoring.rename.rewriteUnavailable", newName))

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
