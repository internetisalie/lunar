package net.internetisalie.lunar.lang.insight

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
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
 *
 * The gate is [LuaDeclarationSite] (REFACT-01 design §3.8): the search target is normalised to a
 * declaration IDENTIFIER leaf first, so a `LuaNameRef` composite (what in-place rename hands the
 * platform) and an elevated declaration node (what Safe Delete passes) are searchable too, and
 * anything that is not a declaration site still returns nothing.
 */
class LuaNameReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(
        parameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val requested = parameters.elementToSearch
        // Labels are the default named-element searcher's business. This guard is UNREACHABLE
        // defence-in-depth: the kindOf gate below already rejects a normalised label, because
        // kindOf of a LuaLabelName's IDENTIFIER child is null (its parent is a LuaLabelName, not a
        // LuaNameRef). Keep it and keep it HERE — it becomes the only exclusion the moment
        // LuaDeclarationSite gains a row that classifies a label's leaf. Do not delete it as dead
        // code and do not fold it after the normalisation.
        if (requested is LuaLabelName) return
        val target = LuaDeclarationSite.identifierLeafOf(requested) ?: return
        if (LuaDeclarationSite.kindOf(target) == null) return
        // Read from the NORMALISED leaf: a composite's text is the whole declaration
        // ("local x = 1"), which matches no identifier and asks the word index for a word with
        // spaces in it — zero usages, looking healthy.
        val name = target.text
        if (name.isEmpty()) return

        for (file in candidateFiles(target, name, parameters.effectiveSearchScope)) {
            ProgressManager.checkCanceled()
            for (nameRef in PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java)) {
                if (nameRef.identifier?.text != name) continue
                val reference = nameRef.reference ?: continue
                // Against the normalised leaf, never `requested`: isReferenceTo compares identity
                // against resolve(), which always returns a leaf.
                if (reference.isReferenceTo(target) && !consumer.process(reference)) return
            }
        }
    }

    /**
     * Files that may contain a usage: those the word index records as containing [name] in code,
     * intersected with the requested scope. A [LocalSearchScope] is handled by reading its scope
     * elements' files directly.
     */
    private fun candidateFiles(
        target: PsiElement,
        name: String,
        scope: Any,
    ): Collection<PsiFile> =
        when (scope) {
            is GlobalSearchScope ->
                CacheManager
                    .getInstance(target.project)
                    .getFilesWithWord(name, UsageSearchContext.IN_CODE, scope, true)
                    .toList()
            is LocalSearchScope ->
                scope.scope.mapNotNull { it.containingFile }.distinct()
            else -> emptyList()
        }
}
