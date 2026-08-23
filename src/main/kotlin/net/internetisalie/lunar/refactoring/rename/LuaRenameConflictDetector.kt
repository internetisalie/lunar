package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo
import com.intellij.usageView.UsageInfo
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.LuaScopeProcessor
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.navigation.LuaGlobalAssignmentNavigation
import net.internetisalie.lunar.lang.psi.LuaDeclarationKind
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaResolveUtil
import java.util.Collections
import java.util.IdentityHashMap

/** The declaration being renamed, its classification and the name it is becoming (design §2.3). */
internal data class LuaRenameTarget(
    val identifier: PsiElement,
    val kind: LuaDeclarationKind,
    val newName: String,
)

/**
 * A conflict the platform must show before anything is written (design §2.4).
 *
 * `UnresolvableCollisionUsageInfo` is the carrier because
 * `RenameProcessor.preprocessUsages` turns each one into a conflicts-dialog entry keyed on
 * `usage.element` (`RenameUtil.addConflictDescriptions`) and then **removes it from the usage set**
 * (`RenameUtil.removeConflictUsages`, `RenameProcessor.java:248-252`). Anchoring a collision on a
 * usage that still needs rewriting would therefore skip rewriting it when the user presses
 * Continue — the silent-partial rename of BUG-457, reintroduced by the machinery meant to warn
 * about it. Every anchor [LuaRenameConflictDetector] produces is a *colliding declaration* or a
 * *foreign reference already named `newName`*, never a member of the renamed symbol's usage set.
 */
internal class LuaRenameCollisionUsageInfo(
    anchor: PsiElement,
    renamedDeclaration: PsiElement,
    private val message: String,
) : UnresolvableCollisionUsageInfo(anchor, renamedDeclaration) {
    override fun getDescription(): String = message
}

/**
 * Reports the renames that would silently **rebind** rather than fail (REFACT-01-14, design §3.4).
 *
 * Lua has no compiler to catch a rename that changes meaning: the file still parses, still runs,
 * and reads a different value. Four rules, all expressed as existing lookups — two
 * [LuaResolveUtil.scopeCrawlUp] crawls and two index reads — so no new scope model is introduced
 * and no rule walks the project's PSI looking for candidates.
 *
 * That is a statement about *how candidates are found*, not a claim that no PSI is loaded:
 * normalising an index hit to its identifier leaf goes through `getNode()`, which loads and parses
 * a stub-backed element's file (`StubBasedPsiElementBase`: "occasionally expensive `getNode()`
 * calls that have to load and parse the AST anew"). [globalDeclarationsNamed] therefore parses one
 * file per hit and is guarded accordingly.
 *
 * **Threading**: called only from `LuaRenameProcessor.findCollisions`, i.e. inside the background
 * read action `BaseRefactoringProcessor` wraps around `findUsages`. Never the EDT.
 *
 * **Cancellation — the invariant, deliberately not a block count.** *Every iteration block in this
 * file whose body can **load** PSI (i.e. force a parse), read VFS, or query an index opens with
 * `ProgressManager.checkCanceled()`. Every other block does bounded work over a list some guarded
 * block upstream has already materialised — an allocation, an identity test, a message lookup, or a
 * `UsageInfo` wrapper around PSI already in hand — and is left unguarded on purpose: a check there
 * would dilute the signal without bounding anything.*
 *
 * **The property is stated instead of a count because three successive counts were all wrong.**
 * Phase 3 recorded six and read as exhaustive. Phase 4 corrected it to seven and read as
 * exhaustive. A chain-count at the Phase-4 review found nine, and a strict count of every
 * lambda-bodied operator finds **eleven** — the earlier figures collapse `filter { }.map { }`
 * chains in [shadows] and [ambiguousGlobal] into one block each, and drop [collisions]' own
 * closing `.map`. Not one of those revisions changed whether the code was compliant; each only
 * changed a number that has to be re-derived by hand after every edit, and would be wrong again
 * after the next one. The invariant above is stable under editing and a reader can check it
 * without recounting anything.
 *
 * **To check it, ask of each lambda body: can this reach PSI, VFS or an index?** Two answers are
 * counter-intuitive and are the two defects the earlier records were written to close:
 * - [captures]' `usages.mapNotNull { it.element }` **is a parse**, though it reads like a field
 *   access. `UsageInfo.getElement()` goes through a soft `SmartPsiElementPointer` whose
 *   `SelfElementInfo.restoreElement` ends at `PsiManager.findFile(vfile)`.
 * - [globalDeclarationsNamed]' `.map` **is a parse per stub hit**, though the index read beside it
 *   looks like the expensive half. `LuaDeclarationSite.identifierLeafOf` reaches a `LuaFuncDecl`'s
 *   name through `getNode()`, which loads and parses a stub-backed element's file.
 *
 * `LuaRenameConflictTest`'s three `testCancellationIsChecked…` cases pin the per-iteration property
 * differentially — one over stub-hit count, one over usage count, one over the file's name-ref count
 * — so none can be satisfied by the entry checks alone. Three, not two, because the first two both
 * use a global target: [shadows] runs only for a file-local kind and was pinned by nothing until a
 * case with a `local` target existed.
 */
internal object LuaRenameConflictDetector {
    /**
     * Two arguments: the third value of the rule set (the new name) is folded into
     * [LuaRenameTarget] rather than passed alongside it.
     *
     * [usages] must be a **snapshot** of the list the caller is about to append to —
     * `RenameUtil.findUsages` hands `findCollisions` the very list `processUsages` filled
     * (`RenameUtil.java:97-103`).
     */
    fun collisions(
        target: LuaRenameTarget,
        usages: List<UsageInfo>,
    ): List<LuaRenameCollisionUsageInfo> {
        val found =
            captures(target, usages) +
                if (target.kind.isFileLocal) {
                    shadows(target)
                } else {
                    globalNameTaken(target) + ambiguousGlobal(target)
                }
        return distinctByAnchor(found).map { LuaRenameCollisionUsageInfo(it.anchor, target.identifier, it.message) }
    }

    /**
     * A conflict before it is wrapped, so that de-duplication compares the anchor elements
     * themselves rather than what `UsageInfo.getElement()` restores from a smart pointer.
     */
    private data class LuaRenameCollision(
        val anchor: PsiElement,
        val message: String,
    )

    /**
     * **C1** — a usage of the renamed symbol would be captured by a declaration of `newName` that
     * is already visible where that usage sits.
     *
     * The declaration site is scanned alongside the usages because a `newName` declared *before*
     * it captures the declaration's own initialiser scope; a `newName` declared *after* it is
     * invisible there but still captures the later usages, which is why neither site alone is
     * enough. `scopeCrawlUp` stops at the site's own declaring statement and
     * `LuaBlock.processDeclarations` stops at that statement's text offset, so only declarations
     * lexically visible at the site are seen — exactly the rebinding hazard.
     */
    private fun captures(
        target: LuaRenameTarget,
        usages: List<UsageInfo>,
    ): List<LuaRenameCollision> {
        val sites =
            usages.mapNotNull { usage ->
                ProgressManager.checkCanceled()
                usage.element
            } + target.identifier
        return sites.mapNotNull { site ->
            ProgressManager.checkCanceled()
            visibleDeclarationOf(target.newName, site)?.let { capturing ->
                LuaRenameCollision(
                    capturing,
                    LuaBundle.message("refactoring.rename.conflict.capture", target.newName, target.identifier.text),
                )
            }
        }
    }

    /**
     * **C2** — an existing reference to `newName` would be captured by the renamed declaration.
     * File-local kinds only; a global rename cannot shadow, it merges (C3).
     */
    private fun shadows(target: LuaRenameTarget): List<LuaRenameCollision> =
        PsiTreeUtil
            .findChildrenOfType(target.identifier.containingFile, LuaNameRef::class.java)
            .filter { reference ->
                ProgressManager.checkCanceled()
                wouldBeCapturedByRename(target, reference)
            }.map { LuaRenameCollision(it, LuaBundle.message("refactoring.rename.conflict.shadow")) }

    /**
     * C2 steps 2-5. The declaration-site skip in the middle is load-bearing: a [LuaNameRef] named
     * `newName` that is itself a *declaration* introduces a new binding rather than reading an
     * existing one, so the renamed declaration shadowing it changes nothing that is already there.
     * Without the skip, `local x = 1; print(x); do local y = 3 end` renamed `x`→`y` reports a
     * conflict that does not exist — measured, and pinned by
     * `LuaRenameConflictTest.testUnrelatedInnerDeclarationIsNotAConflict`.
     */
    private fun wouldBeCapturedByRename(
        target: LuaRenameTarget,
        reference: LuaNameRef,
    ): Boolean {
        val referenceLeaf = reference.identifier
        if (referenceLeaf.text != target.newName) return false
        if (LuaDeclarationSite.kindOf(referenceLeaf) != null) return false
        return visibleDeclarationOf(target.identifier.text, reference) === target.identifier
    }

    /**
     * **C3** — the global name is already taken, so renaming would merge two distinct globals into
     * one `_ENV` entry. Non-file-local kinds only.
     */
    private fun globalNameTaken(target: LuaRenameTarget): List<LuaRenameCollision> {
        val newKey = searchKeyOf(target, target.newName)
        return globalDeclarationsNamed(target, newKey).map { existing ->
            LuaRenameCollision(existing, LuaBundle.message("refactoring.rename.conflict.globalExists", newKey))
        }
    }

    /**
     * **C4** — the global being renamed is declared in more than one place, so its usages do not
     * resolve and will not be rewritten.
     *
     * `LuaNameReference.resolve()` returns null whenever `multiResolve` yields more than one result
     * and `isReferenceTo` is false on a null resolve, so with two declarations of `config` *every*
     * read of `config` project-wide stops being a findable reference. The rename would then rewrite
     * the chosen declaration and nothing else — the silent-partial defect arriving through
     * resolution instead of through classification. Reported rather than refused: the conflicts
     * dialog lists every site and the user can still cancel, whereas refusing would have to run
     * these lookups on the EDT.
     */
    private fun ambiguousGlobal(target: LuaRenameTarget): List<LuaRenameCollision> {
        val oldKey = searchKeyOf(target, target.identifier.text)
        val declarations = globalDeclarationsNamed(target, oldKey)
        if (declarations.size < 2) return emptyList()
        val message = LuaBundle.message("refactoring.rename.conflict.ambiguousGlobal", oldKey, declarations.size)
        return declarations.filter { it !== target.identifier }.map { LuaRenameCollision(it, message) }
    }

    /**
     * The `LuaGlobalDeclarationIndex` key under which [segment] would be declared *as a name of the
     * same thing [target] names* — i.e. [segment] carrying [target]'s own receiver prefix.
     *
     * **Without this, C3 and C4 are inert for every dotted function**, which is the shape of defect
     * this whole feature exists against. Measured on the builder, two files each declaring
     * `function M.run() end`: `LuaFuncStubElementType.indexStub` sinks that decl under the
     * FUNC_NAME node's text `"M.run"` and — via `substringBefore('.')` — under the receiver `"M"`,
     * but **never** under `"run"`; `LuaDeclarationSite.identifierLeafOf` hands rename the LAST
     * segment, so the bare `target.identifier.text` these two rules used to search is a key nothing
     * writes. Both stub reads returned 0, both rules stayed silent, and the rename rewrote the
     * declaration while `ReferencesSearch` found **0** of its call sites — BUG-457's shape inside
     * the feature that closed BUG-457.
     *
     * The prefix is taken by TEXT OFFSET rather than by `substringBeforeLast('.')` because the key
     * is the FUNC_NAME node's text verbatim: this reproduces whatever separators and spacing that
     * node carries, so `function A.B.run()` searches `"A.B.run"` and the colon form searches
     * `"Obj:m"` with no per-shape rule. Measured for all three: prefixes `"M."`, `"A.B."` and `""`.
     *
     * **Bare globals are unaffected, by construction and by measurement.** `function greet() end`
     * has a `funcName` whose only segment IS the leaf, so the prefix is empty; `config = {}` and
     * the Lua 5.5 `global` forms have no `LuaFuncName` ancestor at all. Both return [segment]
     * unchanged, which is what these rules searched before.
     */
    private fun searchKeyOf(
        target: LuaRenameTarget,
        segment: String,
    ): String = receiverPrefixOf(target.identifier) + segment

    /**
     * Everything the declaration's own name chain carries in front of its last segment — `"M."` for
     * `function M.run()`, `""` for `function greet()` and for any leaf that is not part of a
     * function name.
     *
     * Non-strict `getParentOfType` because the leaf is inside the `funcName`, and length-guarded
     * rather than `substring`-and-hope: an offset outside the node would be a `StringIndexOutOfBounds`
     * inside a background read action, and an empty prefix degrades to exactly the pre-existing
     * bare-name lookup.
     */
    private fun receiverPrefixOf(leaf: PsiElement): String {
        val funcName = PsiTreeUtil.getParentOfType(leaf, LuaFuncName::class.java, /* strict = */ false) ?: return ""
        val prefixLength = leaf.textRange.startOffset - funcName.textRange.startOffset
        if (prefixLength <= 0 || prefixLength > funcName.textLength) return ""
        return funcName.text.substring(0, prefixLength)
    }

    /**
     * Every project-wide declaration filed under the index key [key], normalised to its IDENTIFIER
     * leaf so that the two lookups are comparable and C4's `!== target.identifier` test can fire at
     * all.
     *
     * [key] is an index key, not a user-facing name: for a dotted function it is the qualified
     * `"M.run"` that [searchKeyOf] builds, because that is what the stub sinks. The second lookup,
     * [LuaGlobalAssignmentNavigation.find], is keyed on undotted names only — `dottedMemberName`
     * returns null for a bare target and `LuaGlobalAssignmentIndex` records nothing else — so for a
     * dotted key it is an index read that cannot hit, and the dotted candidate set is the stub
     * index alone. A dotted *field assignment* (`M.run = function() end`) is therefore not counted,
     * although `LuaNameReference` does resolve through it; that residual is measured and recorded
     * as `risks-and-gaps.md` Gap 2.15 rather than closed here, because counting it would anchor a
     * collision on an element that is also a **usage** — which the platform then deletes from the
     * usage set (see [LuaRenameCollisionUsageInfo]).
     *
     * Both *candidate sets* come from indexes, so nothing scans the project to find them, and both
     * C3 and C4 share this one lookup — C4 against the old name, C3 against the new one — which is
     * why C4 adds no new cost class.
     *
     * **The normalisation is not free, and the index read is the cheap half.**
     * [LuaDeclarationSite.identifierLeafOf] reaches a `LuaFuncDecl`'s name through
     * `funcDeclNameLeafOf` → `declaration.node`, and `getNode()` on a stub-backed element loads and
     * parses its file (`StubBasedPsiElementBase`). So this `map` parses one file per stub hit,
     * twice per rename, unbounded by project size. `ProgressManager.checkCanceled()` therefore
     * opens the loop body rather than only the function, per the engineering contract's
     * every-iteration-block rule — the same reasoning
     * [net.internetisalie.lunar.lang.navigation.LuaGlobalAssignmentNavigation.find] already
     * applies to its own two loops.
     *
     * A declaration whose leaf cannot be found falls back to the declaration node rather than being
     * dropped: dropping it would lower C4's count and could turn a real ambiguity into silence,
     * which is the one outcome this detector exists to prevent.
     */
    private fun globalDeclarationsNamed(
        target: LuaRenameTarget,
        key: String,
    ): List<PsiElement> {
        ProgressManager.checkCanceled()
        val targetProject = target.identifier.project
        val projectScope = GlobalSearchScope.projectScope(targetProject)
        val stubbed =
            StubIndex
                .getElements(LuaGlobalDeclarationIndex.KEY, key, targetProject, projectScope, LuaFuncDecl::class.java)
                .map { declaration ->
                    ProgressManager.checkCanceled()
                    LuaDeclarationSite.identifierLeafOf(declaration) ?: declaration
                }
        return distinctElements(stubbed + LuaGlobalAssignmentNavigation.find(targetProject, key, projectScope))
    }

    /** The declaration of [name] that is lexically visible at [site], or null when there is none. */
    private fun visibleDeclarationOf(
        name: String,
        site: PsiElement,
    ): PsiElement? {
        val scopeProcessor = LuaScopeProcessor(name)
        LuaResolveUtil.scopeCrawlUp(scopeProcessor, site)
        return scopeProcessor.result
    }

    private fun distinctByAnchor(collisions: List<LuaRenameCollision>): List<LuaRenameCollision> {
        val seenAnchors = Collections.newSetFromMap(IdentityHashMap<PsiElement, Boolean>())
        return collisions.filter { seenAnchors.add(it.anchor) }
    }

    private fun distinctElements(elements: List<PsiElement>): List<PsiElement> {
        val seenElements = Collections.newSetFromMap(IdentityHashMap<PsiElement, Boolean>())
        return elements.filter { seenElements.add(it) }
    }
}
