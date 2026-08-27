package net.internetisalie.lunar.refactoring.rename

import com.intellij.lang.ASTNode
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
import net.internetisalie.lunar.lang.LuaNameReference
import net.internetisalie.lunar.lang.psi.LuaDeclarationKind
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLabelRef
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.settings.LuaRefactoringSettings

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
     *
     * [declarationLeafOf] is applied for the reason design §3.6 gives at [renameElement] — **this
     * is a REFACT-01 defect and REFACT-07 is only the first caller to expose it**, so read that
     * KDoc before touching either site. Here the cost of the un-normalised form is narrower: a
     * caller handing this method a [LuaNameRef] composite gets a null [LuaDeclarationSite.kindOf],
     * so the file-local narrowing is skipped and control falls to `super.findReferences` with the
     * caller's own scope. That was measured to produce identical *results* on the in-place route,
     * because `MemberInplaceRenamer` supplies its own `LocalSearchScope`
     * (`MemberInplaceRenamer.java:200-204`) — the defect costs search breadth, not correctness,
     * which is why it carries no test case and no mutant of its own. It is normalised in the same
     * commit as [renameElement] because it is one latent defect at two sites, and repairing only
     * the site that happens to be observable is what leaves the other to resurface.
     */
    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean,
    ): Collection<PsiReference> {
        val declarationLeaf = declarationLeafOf(element)
        val kind =
            LuaDeclarationSite.kindOf(declarationLeaf)
                ?: return super.findReferences(element, searchScope, searchInCommentsAndStrings)
        val effectiveScope = if (kind.isFileLocal) LocalSearchScope(declarationLeaf.containingFile) else searchScope
        return ReferencesSearch.search(declarationLeaf, effectiveScope).findAll()
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
     * **What that paragraph did NOT cover, and this one does: a user's Cancel between two edits.**
     * Resolving everything up front removes the half-apply that a *failure* between two edits
     * produces; it does nothing about an *interruption* between them, which is BUG-468 and which
     * `d8e571e2`'s "everything that can fail now resolves before the first edit" was read as
     * closing. It did not: measured on `5b7c6ca4`, cancelling the live indicator at this method's
     * second `ProgressManager.checkCanceled` left exactly one of three occurrences on the new name
     * with the declaration on the old one, and the refactoring reported success. So the write path
     * now carries **no** cancellation point at all: every rewrite — usages, declaration and the
     * `---@param` tag — is resolved first (design §3.3 steps 2, 3 and 3a) and applied inside one
     * `ProgressManager.getInstance().executeNonCancelableSection` (step 4). Precomputing alone is
     * not enough, also measured: `CompositeElement.replaceChild` (`:647`) reaches
     * `CompositeElement.getPsi()`'s own `checkCanceled` (`:719-720`) through
     * `ChangeUtil.prepareAndRunChangeAction` (`ChangeUtil.java:148`), which evaluates before
     * `PomModelImpl`'s section is entered — declaring the path non-cancelable is what closes it.
     * TC-43 and TC-45 are the gates; TC-44 pins the cancellation point at per-usage.
     *
     * **The residual is the mirror image and is accepted** (`risks-and-gaps.md` Gap 2.18): a Cancel
     * arriving after the preparation phase is ignored and the rename completes. An ignored Cancel
     * with a correct file beats an honoured one with a broken file, the window holds no parse and
     * no index read, its only VFS touch is the document lookup every `replaceChild` makes through
     * `PomModelImpl.startTransaction` (`PomModelImpl.java:310-311`,
     * `FileDocumentManager.getDocument(vFile)`) — already warmed by the preparation phase, which
     * dereferences `usage.element` and `host.node` for every usage and so forces each file's
     * document — and the result is a single undoable command.
     *
     * **Step 3a's `---@param` propagation is resolved with the declaration leaf, BEFORE the
     * declaration swap, and applied last inside the section.** That ordering removes the dependency
     * the previous shape had — it resolved from [replacement] and rested on `replaceChild` leaving
     * the ancestor chain intact — rather than restating it. The lookup is hoisted out of the section because it
     * expands a `LuaCatsLazyCommentImpl` chameleon, i.e. it parses, on an input sized by the user's
     * doc comment (design §3.6). [LuaCatsParamRenamer] still has no failure outcome, so it cannot
     * abort a rename that is already applied, and TC-20d pins the other direction: a REFUSED rename
     * never reaches it, so the tag does not move on its own.
     *
     * **[declarationLeafOf] normalises the argument first, and that is a `REFACT-01` defect that
     * REFACT-07 is merely the first caller to expose — it is NOT an in-place workaround.** This
     * method classifies with [LuaDeclarationSite.kindOf], which is null for anything whose node
     * type is not `IDENTIFIER`, so **any** caller handing it a [LuaNameRef] composite silently
     * loses the `---@param` clause below. Nothing did until now only because the dialog path
     * substitutes to the leaf first ([substituteElementToRename]); the in-place route does not,
     * because `MemberInplaceRenamer.getSubstituted()` re-derives the target through
     * `findElementOfClassAtRange(…, PsiNameIdentifierOwner.class)`, which REFACT-07's grant of that
     * interface to [LuaNameRef] makes the composite. A reader who files this as in-place-specific
     * will delete it the next time the in-place path changes, and `REFACT-07-09` will regress
     * silently — with `LuaCatsParamRenameTest` still green, because the dialog path never sees the
     * composite. The normalisation must survive REFACT-07 entirely. Design §3.6.
     */
    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<UsageInfo>,
        listener: RefactoringElementListener?,
    ) {
        val declarationLeaf = declarationLeafOf(element)
        val declarationKind = LuaDeclarationSite.kindOf(declarationLeaf)
        val oldName = declarationLeaf.text
        val replacement =
            LuaElementFactory.createIdentifier(element.project, newName) ?: refuseRewrite(newName)
        val applyDeclarationRewrite = preparedDeclarationRewrite(declarationLeaf, replacement) ?: refuseRewrite(newName)
        val applyUsageRewrites = preparedUsageRewrites(usages, newName)
        val applyCatsTagRewrite =
            if (declarationKind == LuaDeclarationKind.PARAMETER) {
                LuaCatsParamRenamer.preparedRename(declarationLeaf, oldName, newName)
            } else {
                null
            }
        ProgressManager.getInstance().executeNonCancelableSection {
            applyUsageRewrites.forEach { applyRewrite -> applyRewrite() }
            applyDeclarationRewrite()
            applyCatsTagRewrite?.invoke()
        }
        listener?.elementRenamed(replacement)
    }

    /**
     * Design §2.9 — REFACT-01-15. Whether the rename dialog's "Search in comments and strings" box
     * starts ticked, and where that choice is remembered.
     *
     * The base implementation is `element instanceof PsiFileSystemItem && …`
     * (`RenamePsiElementProcessorBase.java:195-212`) — a hard `false` for an identifier, with a
     * setter that discards the user's answer — and the platform's own `RefactoringSettings` has no
     * `…_FOR_VARIABLE` field to delegate to, so [LuaRefactoringSettings] is where it lives.
     */
    override fun isToSearchInComments(element: PsiElement): Boolean =
        LuaRefactoringSettings.instance.renameSearchInComments

    override fun setToSearchInComments(
        element: PsiElement,
        enabled: Boolean,
    ) {
        LuaRefactoringSettings.instance.renameSearchInComments = enabled
    }

    /** Design §2.9 — the "Search for text occurrences" half of the same pair. */
    override fun isToSearchForTextOccurrences(element: PsiElement): Boolean =
        LuaRefactoringSettings.instance.renameSearchForText

    override fun setToSearchForTextOccurrences(
        element: PsiElement,
        enabled: Boolean,
    ) {
        LuaRefactoringSettings.instance.renameSearchForText = enabled
    }

    /**
     * Design §2.9 — the element the SEARCHED string is derived from, which is not the element
     * being renamed.
     *
     * `RenameUtil.processUsages` takes the string to look for from
     * `ElementDescriptionUtil.getElementDescription(searchForInComments, STRINGS_AND_COMMENTS)`
     * (`RenameUtil.java:145-155`), whose only general branch is
     * `element instanceof PsiNamedElement -> getName()`
     * (`DefaultNonCodeSearchElementDescriptionProvider.java:37-40`). The renamed element here is a
     * bare IDENTIFIER **leaf**, which is not a `PsiNamedElement`, so the platform default — which
     * returns [element] unchanged (`RenamePsiElementProcessorBase.java:264-266`) — makes
     * `ElementDescriptionUtil` fall through to `return element.toString()`
     * (`ElementDescriptionUtil.java:26`). The comment search would then run against a
     * `LeafPsiElement`'s DEBUG STRING, match nothing, and leave the checkbox inert while appearing
     * to work. Returning the enclosing [LuaNameRef] — a `PsiNamedElement` whose `getName()` is the
     * identifier text (`LuaBaseElements.kt:75, 81`) — makes the searched string the name.
     *
     * `null` is the deliberate answer for the one declaration kind with no [LuaNameRef] parent:
     * a numeric-`for` variable, whose leaf hangs directly off `LuaNumericForStatement`
     * (`lua.bnf:152`). `RenameUtil.processUsages` guards both non-code branches with
     * `searchForInComments != null` (`RenameUtil.java:147, 157`), so a null disables non-code
     * search for that kind instead of searching for garbage. TC-13c pins both halves.
     */
    override fun getElementToSearchInStringsAndComments(element: PsiElement): PsiElement? =
        element.parent as? LuaNameRef

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

    /**
     * Design §3.6 — the declaration IDENTIFIER **leaf** [element] names, or [element] itself.
     *
     * `identifierLeafOf` is the repo's existing normaliser and is total in both directions: a leaf
     * maps to itself, and a declaration node or a [LuaNameRef] composite maps down to its leaf.
     *
     * **`?: element` is the fallback, and it must not become `?: return`.** `identifierLeafOf`
     * answers null for an element that names no declaration — a *usage* [LuaNameRef] — and falling
     * back to the raw element makes this normalisation **behaviour-preserving for every caller that
     * works today**: [LuaDeclarationSite.kindOf] of such an element was already null and stays null.
     * An early return would turn a rename that currently proceeds into a silent no-op.
     *
     * The leaf is used for the **rewrite** as well as for the classification.
     * [preparedDeclarationRewrite] replaces `element.node` inside `element.parent.node`, so handed
     * the composite it would swap a whole `NAME_REF` node for a bare `IDENTIFIER` leaf — a tree
     * `nameRef ::= IDENTIFIER` (`lua.bnf:169`) forbids.
     */
    private fun declarationLeafOf(element: PsiElement): PsiElement =
        LuaDeclarationSite.identifierLeafOf(element) ?: element

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
     * Every usage's rewrite, resolved but NOT yet applied (design §3.3 step 3).
     *
     * **`ProgressManager.checkCanceled()` is the first statement of every iteration, and it is the
     * only cancellation point left anywhere on [renameElement]'s write path.** It is safe here for
     * exactly the reason it was never safe in the apply loop this replaces: this loop writes
     * nothing, so a cancellation leaves the file untouched instead of half-renamed. It is also
     * where the per-usage cost lives — a `SmartPsiElementPointer` deref that can restore a file
     * (`UsageInfo.getElement()`) and a parse per usage — which is what satisfies
     * engineering-contract §2's cancellation rule while step 4 carries no check at all.
     *
     * **`claimedIdentifierNodes` is not defensive — it repairs a hazard the hoist itself creates,
     * and it was measured rather than anticipated.** The usage array can hold TWO entries over the
     * SAME host: renaming `function M.run()` with a collision acknowledged (TC-42) yields a
     * `MoveRenameUsageInfo` and a [LuaRenameCollisionUsageInfo] over one identical `LuaNameRefImpl`,
     * both carrying a [LuaNameReference]. The shipped `RenameUtil.rename` path absorbed that: it
     * re-read the host's current IDENTIFIER child inside `setName` on each call, so a second rewrite
     * of one occurrence was a no-op. Hoisting that lookup out — which is the whole point of design
     * §3.3 step 3 — captures the node instead, so the first closure detaches it and the second trips
     * `CompositeElement.replaceChild`'s `LOG.assertTrue(oldChild.getTreeParent() == this)` (`:648`).
     * Preparing at most one rewrite per IDENTIFIER node reproduces the old net effect exactly, and
     * it belongs HERE rather than in the closure because re-reading at apply time would put a lookup
     * back inside the non-cancelable section. Design §3.3 does not state this precondition; see
     * `risks-and-gaps.md` Gap 2.19.
     */
    private fun preparedUsageRewrites(
        usages: Array<UsageInfo>,
        newName: String,
    ): List<() -> Unit> {
        val claimedIdentifierNodes = mutableSetOf<ASTNode>()
        return usages.mapNotNull { usage ->
            ProgressManager.checkCanceled()
            preparedUsageRewrite(usage, newName, claimedIdentifierNodes)
        }
    }

    /**
     * One usage's AST swap, resolved but NOT yet applied — the same swap `LuaNameRef.setName`
     * performs (`LuaBaseElements.kt:83-92`) with the parse hoisted out of it — or `null` when the
     * platform's own rename would not have rewritten this usage either.
     *
     * The two nulls are **exactly** `RenameUtilBase.rename`'s early returns
     * (`RenameUtilBase.java:90-95`), so skipping them is what the shipped code already does rather
     * than a new omission. A `NonCodeUsageInfo` takes that branch — its `PsiCommentImpl` host has a
     * null `getReference()` — and is rewritten elsewhere, by `RenameUtil.renameNonCodeUsages` from
     * `performPsiSpoilingRefactoring`, never here.
     *
     * A usage that is not a [LuaNameReference], or whose host has no IDENTIFIER child, gets the
     * DELEGATING closure rather than a `return null`, because a silent skip is the half-apply class
     * this method exists to remove. That branch is unreachable today — [LuaNameReference] is the
     * only `PsiReference` in `src/main/` that can be a usage of a declaration IDENTIFIER leaf, the
     * other two resolving to a `LuaLabelName` (excluded by design §3.0) and to a `PsiFile` — and it
     * is the one thing inside step 4's section that parses, which design §3.3 and
     * `risks-and-gaps.md` Gap 2.18 both state rather than gloss.
     *
     * A second usage over an IDENTIFIER node an earlier one already claimed prepares NOTHING, and
     * that is the one place a `return null` here is not a skipped rewrite: the occurrence is already
     * being rewritten by the closure that claimed it. See [preparedUsageRewrites] for the measured
     * case that requires it.
     *
     * The `createIdentifier` refusal cannot fire and is written as a refusal anyway: the factory is
     * a pure function of `(project, newName)` and [renameElement] already got a non-null answer for
     * the same pair, so a null here is unreachable rather than unlikely. No test pins it and none
     * can; if the factory ever became non-deterministic the outcome would be a refusal rather than
     * a silently skipped usage.
     */
    private fun preparedUsageRewrite(
        usage: UsageInfo,
        newName: String,
        claimedIdentifierNodes: MutableSet<ASTNode>,
    ): (() -> Unit)? {
        val host = usage.element ?: return null
        val reference = usage.reference ?: return null
        val hostNode = host.node
        val identifierNode =
            if (reference is LuaNameReference) hostNode?.findChildByType(LuaElementTypes.IDENTIFIER) else null
        if (hostNode == null || identifierNode == null) return { RenameUtil.rename(usage, newName) }
        if (!claimedIdentifierNodes.add(identifierNode)) return null
        val replacementNode =
            LuaElementFactory.createIdentifier(host.project, newName)?.node ?: refuseRewrite(newName)
        return { hostNode.replaceChild(identifierNode, replacementNode) }
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
