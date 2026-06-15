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
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaFuncNameMethod
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef

/**
 * Safe Delete processor delegate for Lua declarations (REFACT-03).
 *
 * Handles any element that [LuaFindUsagesProvider.canFindUsagesFor] accepts — i.e. local
 * variables, parameters, for-loop variables, local/global functions, methods, and labels.
 *
 * [getElementsToSearch] elevates the raw IDENTIFIER leaf to the nearest whole-declaration
 * node (e.g. [LuaLocalVarDecl]) so that the platform's [com.intellij.ide.util.DeleteHandler]
 * removes the complete statement rather than just the token (REFACT-03-01).
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
     * search (silently orphaning references). [canFindUsagesFor] only accepts IDENTIFIER leaves,
     * so the elevated nodes must be admitted explicitly.
     */
    private fun isElevatedDeclaration(element: PsiElement): Boolean =
        element is LuaLocalVarDecl ||
            element is LuaLocalFuncDecl ||
            element is LuaFuncDecl ||
            element is LuaAttName

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
    ): Collection<PsiElement> = listOf(declarationNodeFor(element))

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
        val searchTarget = identifierLeafFor(element) ?: element
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

    override fun preprocessUsages(project: Project, usages: Array<UsageInfo>): Array<UsageInfo> = usages

    // -------------------------------------------------------------------------
    // Pre-deletion hook — nothing to normalise for Lua declarations
    // -------------------------------------------------------------------------

    @Throws(IncorrectOperationException::class)
    override fun prepareForDeletion(element: PsiElement) {}

    // -------------------------------------------------------------------------
    // Comment / text-occurrence search — not applicable for Lua symbols
    // -------------------------------------------------------------------------

    override fun isToSearchInComments(element: PsiElement): Boolean = false

    override fun setToSearchInComments(element: PsiElement, enabled: Boolean) {}

    override fun isToSearchForTextOccurrences(element: PsiElement): Boolean = false

    override fun setToSearchForTextOccurrences(element: PsiElement, enabled: Boolean) {}

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Walks from the IDENTIFIER leaf up to the most appropriate whole-declaration
     * node.  The platform's [com.intellij.ide.util.DeleteHandler.deleteElementImpl]
     * calls [PsiElement.delete] on whatever we return here, so we return the
     * statement-level container rather than the raw token.
     *
     * - `IDENTIFIER → LuaNameRef → LuaAttName` (local var):
     *     single-name `local x = …` → [LuaLocalVarDecl]; multi-name → [LuaAttName]
     * - `IDENTIFIER → LuaNameRef → LuaLocalFuncDecl`: [LuaLocalFuncDecl]
     * - `IDENTIFIER → LuaNameRef → LuaFuncName/LuaFuncNameMethod`: [LuaFuncDecl]
     * - All other cases (labels, for-vars, parameters): return [element] itself
     */
    private fun declarationNodeFor(element: PsiElement): PsiElement {
        val parent = element.parent
        if (parent !is LuaNameRef) return element
        return when (val grandParent = parent.parent) {
            is LuaAttName -> {
                val decl = grandParent.parent as? LuaLocalVarDecl ?: return grandParent
                if (decl.attNameList.size == 1) decl else grandParent
            }
            is LuaLocalFuncDecl -> grandParent
            is LuaFuncName ->
                grandParent.parent as? LuaFuncDecl ?: grandParent
            is LuaFuncNameMethod ->
                grandParent.parent?.parent as? LuaFuncDecl ?: grandParent
            else -> element
        }
    }

    /**
     * Extracts the IDENTIFIER leaf from a declaration node so that
     * [ReferencesSearch] can locate usages via [LuaNameReferenceSearcher].
     * Returns null if [element] is already a leaf (or an unrecognised node).
     */
    private fun identifierLeafFor(element: PsiElement): PsiElement? = when (element) {
        is LuaLocalVarDecl -> element.attNameList.firstOrNull()?.nameRef?.identifier
        is LuaLocalFuncDecl -> element.nameRef.identifier
        is LuaFuncDecl -> {
            val funcName = element.funcName
            funcName.funcNameMethod?.nameRef?.identifier ?: funcName.nameRef.identifier
        }
        else -> null
    }

    private companion object {
        val findUsagesProvider = LuaFindUsagesProvider()
    }
}
