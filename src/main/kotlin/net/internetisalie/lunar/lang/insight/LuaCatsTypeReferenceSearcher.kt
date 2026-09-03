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
import net.internetisalie.lunar.lang.LuaCatsTypeReference
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsGenericType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsNamedType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeParam

/**
 * Reference search for a LuaCATS type-name declaration leaf (`REFACT-08` design.md §2.5, §3.5).
 *
 * **Why this exists at all**, the cats-comment sibling of [LuaNameReferenceSearcher]'s reason:
 * `CachesBasedRefSearcher` derives a search text only for a `PsiFileSystemItem`, a
 * `PsiNamedElement` or a `PsiMetaOwner` (`CachesBasedRefSearcher.java:26-56`), and a bare LuaCATS
 * NAME leaf is none of the three — so its `text` is null and the default searcher scans nothing.
 * **Its necessity is measured, not assumed**: `REFACT-08-00-DR-03` mutation O removed this EP
 * registration and every reference resolution in the feature (rename, Find Usages, Ctrl+Click)
 * turned to `references=0` — the declaration then moves alone and every use is left stale.
 *
 * `IN_COMMENTS`, not `IN_CODE`: [net.internetisalie.lunar.lang.insight.LuaFindUsagesProvider]'s
 * word scanner classifies LuaCATS comment tokens as comment words, and a type name is spelled only
 * inside a `---@` comment.
 */
class LuaCatsTypeReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(
        parameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val target = parameters.elementToSearch
        if (!LuaCatsTypeDeclarations.isDeclarationLeaf(target)) return
        val name = target.text
        if (name.isEmpty()) return

        for (file in candidateFiles(target, name, parameters.effectiveSearchScope)) {
            ProgressManager.checkCanceled()
            for (holder in typeHolders(file)) {
                ProgressManager.checkCanceled()
                if (holder.text != name) continue
                val reference = holder.reference as? LuaCatsTypeReference ?: continue
                if (reference.isReferenceTo(target) && !consumer.process(reference)) return
            }
        }
    }

    /** Every use-holder-shaped element in [file] — the three rules `design.md` §3.1 names. */
    private fun typeHolders(file: PsiFile): Sequence<PsiElement> =
        sequence {
            yieldAll(PsiTreeUtil.findChildrenOfType(file, LuaCatsNamedType::class.java))
            yieldAll(PsiTreeUtil.findChildrenOfType(file, LuaCatsTypeParam::class.java))
            yieldAll(PsiTreeUtil.findChildrenOfType(file, LuaCatsGenericType::class.java))
        }

    /**
     * Files that may hold a use: the word index's `IN_COMMENTS` context, intersected with the
     * requested scope. A [LocalSearchScope] is handled by reading its scope elements' files
     * directly, matching [LuaNameReferenceSearcher]'s idiom.
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
                    .getFilesWithWord(name, UsageSearchContext.IN_COMMENTS, scope, true)
                    .toList()
            is LocalSearchScope ->
                scope.scope.mapNotNull { it.containingFile }.distinct()
            else -> emptyList()
        }
}
