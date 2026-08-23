package net.internetisalie.lunar.refactoring

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import net.internetisalie.lunar.lang.insight.LuaFindUsagesProvider
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite

/**
 * Safe Delete processor delegate for Lua declarations (REFACT-03).
 *
 * Handles any element that [LuaFindUsagesProvider.canFindUsagesFor] accepts — i.e. local
 * variables, parameters, for-loop variables, local/global functions, methods, and labels.
 *
 * [getElementsToSearch] elevates the raw IDENTIFIER leaf to the nearest whole-declaration
 * node (e.g. [net.internetisalie.lunar.lang.psi.LuaLocalVarDecl]) so that the platform's
 * [com.intellij.ide.util.DeleteHandler] removes the complete statement rather than just the token
 * (REFACT-03-01). Both directions of that normalisation live in [LuaDeclarationSite] (REFACT-01),
 * shared with Find Usages and reference search so the three cannot disagree about what a Lua
 * declaration site is.
 *
 * [findUsages] extracts the IDENTIFIER leaf from whichever element it receives so that
 * [com.intellij.psi.search.searches.ReferencesSearch] — driven by [LuaNameReferenceSearcher] and
 * [net.internetisalie.lunar.lang.LuaNameReference.isReferenceTo] — finds the correct usages.
 * Found usages are flagged `isSafeDelete = false` (unsafe) so the platform shows the "usages
 * found" conflict dialog when any remain (REFACT-03-03).
 */
class LuaSafeDeleteProcessor : SafeDeleteProcessorDelegateBase() {
    // -------------------------------------------------------------------------
    // Delegation predicate
    // -------------------------------------------------------------------------

    override fun handlesElement(element: PsiElement): Boolean =
        findUsagesProvider.canFindUsagesFor(element) || isElevatedDeclaration(element)

    /**
     * True for the whole-declaration nodes [getElementsToSearch] elevates a leaf to. The platform
     * re-dispatches `handlesElement` on the elevated element before calling [findUsages]; if this
     * returned false the delegate would be dropped and the declaration deleted with NO usage
     * search (silently orphaning references) — none of those nodes is a `PsiNamedElement`, so the
     * platform's generic fallback does not fire either. `canFindUsagesFor` only accepts IDENTIFIER
     * leaves, so the elevated nodes must be admitted explicitly.
     *
     * A round trip, not an enumeration (REFACT-01 design §2.6a): an element is an elevated
     * declaration **iff** it is what [LuaDeclarationSite.declarationNodeOf] elevates its own
     * identifier leaf to. That admits exactly the nodes [getElementsToSearch] can produce, and it
     * cannot fall behind `declarationNodeOf` the way a hand-maintained list does.
     */
    private fun isElevatedDeclaration(element: PsiElement): Boolean {
        val declarationLeaf = LuaDeclarationSite.identifierLeafOf(element) ?: return false
        return LuaDeclarationSite.declarationNodeOf(declarationLeaf) === element
    }

    // -------------------------------------------------------------------------
    // Elements to search / delete (REFACT-03-02)
    //
    // Elevates the declaration IDENTIFIER leaf to the containing whole-declaration
    // PSI node so the platform deletes the full statement (not just the token).
    // -------------------------------------------------------------------------

    override fun getElementsToSearch(
        element: PsiElement,
        module: Module?,
        allElementsToDelete: Collection<PsiElement>,
    ): Collection<PsiElement> = listOf(LuaDeclarationSite.declarationNodeOf(element))

    // -------------------------------------------------------------------------
    // Usage discovery (REFACT-03-02 / REFACT-03-03)
    //
    // [element] may be either the raw IDENTIFIER leaf (when called directly in
    // tests) or the elevated declaration node (when invoked through
    // SafeDeleteHandler → getElementsToSearch).  We normalise to the IDENTIFIER
    // leaf before searching so that LuaNameReferenceSearcher resolves correctly.
    // -------------------------------------------------------------------------

    override fun findUsages(
        element: PsiElement,
        allElementsToDelete: Array<PsiElement>,
        result: MutableList<in UsageInfo>,
    ): NonCodeUsageSearchInfo {
        val searchTarget = LuaDeclarationSite.identifierLeafOf(element) ?: element
        ReferencesSearch.search(searchTarget, searchTarget.useScope).forEach { ref ->
            result.add(SafeDeleteReferenceSimpleDeleteUsageInfo(ref.element, searchTarget, false))
        }
        return NonCodeUsageSearchInfo(
            SafeDeleteProcessor.getDefaultInsideDeletedCondition(allElementsToDelete),
            element,
        )
    }

    // -------------------------------------------------------------------------
    // Optional additional elements — none for Lua (REFACT-03 scope)
    // -------------------------------------------------------------------------

    override fun getAdditionalElementsToDelete(
        element: PsiElement,
        allElementsToDelete: Collection<PsiElement>,
        askUser: Boolean,
    ): Collection<PsiElement>? = null

    // -------------------------------------------------------------------------
    // Post-find preprocessing — pass all usages through unchanged
    // -------------------------------------------------------------------------

    override fun preprocessUsages(
        project: Project,
        usages: Array<UsageInfo>,
    ): Array<UsageInfo> = usages

    // -------------------------------------------------------------------------
    // Pre-deletion hook — nothing to normalise for Lua declarations
    // -------------------------------------------------------------------------

    @Throws(IncorrectOperationException::class)
    override fun prepareForDeletion(element: PsiElement) {}

    // -------------------------------------------------------------------------
    // Comment / text-occurrence search — not applicable for Lua symbols
    // -------------------------------------------------------------------------

    override fun isToSearchInComments(element: PsiElement): Boolean = false

    override fun setToSearchInComments(
        element: PsiElement,
        enabled: Boolean,
    ) {}

    override fun isToSearchForTextOccurrences(element: PsiElement): Boolean = false

    override fun setToSearchForTextOccurrences(
        element: PsiElement,
        enabled: Boolean,
    ) {}

    private companion object {
        val findUsagesProvider = LuaFindUsagesProvider()
    }
}
