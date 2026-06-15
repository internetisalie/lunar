package net.internetisalie.lunar.lang.insight

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.Processor
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaNameRef

/**
 * Reference search for Lua declaration IDENTIFIER leaves (locals, parameters, for-vars,
 * local/global functions, methods).
 *
 * **Why a custom searcher and a manual PSI scan.** A Lua name reference
 * ([net.internetisalie.lunar.lang.LuaNameReference]) is attached to the `LuaNameRef`
 * *composite*, not to the IDENTIFIER leaf inside it (see `LuaBaseElements.getReference`).
 * Resolution and completion walk up from the leaf, so they work — but the platform's
 * word-index search lands on the leaf and inspects *its* references, finds none, and never
 * calls `isReferenceTo`. That left Find Usages / Safe Delete returning 0 usages for locals and
 * cross-file globals (NAV-02-01 "Partial").
 *
 * So instead of `optimizer.searchWord` we use the (correctly populated) word index only to
 * narrow the candidate files via [CacheManager.getFilesWithWord], then PSI-scan each file for
 * `LuaNameRef`s of the right name and feed those whose `isReferenceTo(target)` holds. Scope
 * isolation between same-named declarations is preserved by resolution inside `isReferenceTo`.
 *
 * Labels are skipped: [LuaLabelName] is a `PsiNamedElement` whose reference the default searcher
 * already drives.
 */
class LuaNameReferenceSearcher :
    QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        parameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val target = parameters.elementToSearch
        if (!isNameDeclarationLeaf(target)) return
        val name = target.text
        if (name.isEmpty()) return

        for (file in candidateFiles(target, name, parameters.effectiveSearchScope)) {
            for (nameRef in PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java)) {
                if (nameRef.identifier?.text != name) continue
                val reference = nameRef.reference ?: continue
                if (reference.isReferenceTo(target) && !consumer.process(reference)) return
            }
        }
    }

    /**
     * Files that may contain a usage: those the word index records as containing [name] in code,
     * intersected with the requested scope. A [LocalSearchScope] is handled by reading its scope
     * elements' files directly.
     */
    private fun candidateFiles(target: PsiElement, name: String, scope: Any): Collection<PsiFile> =
        when (scope) {
            is GlobalSearchScope ->
                CacheManager.getInstance(target.project)
                    .getFilesWithWord(name, UsageSearchContext.IN_CODE, scope, true)
                    .toList()
            is LocalSearchScope ->
                scope.scope.mapNotNull { it.containingFile }.distinct()
            else -> emptyList()
        }

    /**
     * True when [element] is a Lua declaration IDENTIFIER leaf that
     * [LuaFindUsagesProvider.canFindUsagesFor] accepts — but NOT a [LuaLabelName],
     * which the default named-element searcher already covers.
     */
    private fun isNameDeclarationLeaf(element: PsiElement): Boolean {
        if (element is LuaLabelName) return false
        if (element.elementType != LuaElementTypes.IDENTIFIER) return false
        return usagesProvider.canFindUsagesFor(element)
    }

    private companion object {
        val usagesProvider = LuaFindUsagesProvider()
    }
}
