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
 * **Cancellation: SEVEN iteration blocks, not six.** The Phase-3 record said six and read as
 * exhaustive; the omitted one was [captures]' `usages.mapNotNull { it.element }`, and it was the
 * only omitted block able to reach PSI and VFS — `UsageInfo.getElement()` goes through a soft
 * `SmartPsiElementPointer`, whose `SelfElementInfo.restoreElement` ends at
 * `PsiManager.findFile(vfile)`, i.e. a parse. It is now guarded like the rest. The three blocks
 * that can load PSI — that one, [shadows]' file-wide `LuaNameRef` scan and
 * [globalDeclarationsNamed]' per-hit normalisation — all check at the top of the body. The
 * remaining four ([globalNameTaken]'s and [ambiguousGlobal]'s message maps and the two
 * identity-set dedupe filters) are allocation over already-materialised, already-guarded lists and
 * are deliberately left unguarded; checks there would dilute the signal without bounding anything.
 *
 * `LuaRenameConflictTest`'s two `testCancellationIsChecked…` cases pin the per-iteration property
 * differentially — one over stub-hit count, one over usage count — so neither can be satisfied by
 * the entry checks alone.
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
    private fun globalNameTaken(target: LuaRenameTarget): List<LuaRenameCollision> =
        globalDeclarationsNamed(target, target.newName).map { existing ->
            LuaRenameCollision(existing, LuaBundle.message("refactoring.rename.conflict.globalExists", target.newName))
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
        val oldName = target.identifier.text
        val declarations = globalDeclarationsNamed(target, oldName)
        if (declarations.size < 2) return emptyList()
        val message = LuaBundle.message("refactoring.rename.conflict.ambiguousGlobal", oldName, declarations.size)
        return declarations.filter { it !== target.identifier }.map { LuaRenameCollision(it, message) }
    }

    /**
     * Every project-wide declaration of [name], normalised to its IDENTIFIER leaf so that the two
     * lookups are comparable and C4's `!== target.identifier` test can fire at all.
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
        name: String,
    ): List<PsiElement> {
        ProgressManager.checkCanceled()
        val targetProject = target.identifier.project
        val projectScope = GlobalSearchScope.projectScope(targetProject)
        val stubbed =
            StubIndex
                .getElements(LuaGlobalDeclarationIndex.KEY, name, targetProject, projectScope, LuaFuncDecl::class.java)
                .map { declaration ->
                    ProgressManager.checkCanceled()
                    LuaDeclarationSite.identifierLeafOf(declaration) ?: declaration
                }
        return distinctElements(stubbed + LuaGlobalAssignmentNavigation.find(targetProject, name, projectScope))
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
