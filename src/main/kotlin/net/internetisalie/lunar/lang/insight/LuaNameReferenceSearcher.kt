package net.internetisalie.lunar.lang.insight

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.elementType
import com.intellij.util.Processor
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLabelName

/**
 * Drives the word-index reference search for Lua declaration IDENTIFIER leaves.
 *
 * Lua name declarations resolve to a raw IDENTIFIER leaf (the value returned by
 * [net.internetisalie.lunar.lang.LuaNameReference.resolve]).  That leaf is not a
 * [com.intellij.psi.PsiNamedElement], so the platform's default reference searcher
 * (`CachesBasedRefSearcher`) computes no search word for it and never visits the
 * usage references — Find Usages returned 0 results (NAV-02-01 "Partial").
 *
 * This searcher supplies the missing word: for a declaration IDENTIFIER leaf it
 * calls `searchWord(identifierText, effectiveScope, target)`.  The platform's
 * `SingleTargetRequestResultProcessor` then keeps only occurrences whose
 * `reference.isReferenceTo(target)` is true — i.e. those whose
 * `LuaNameReference.resolve()` returns this exact leaf.  Scope isolation between
 * same-named locals is therefore preserved by resolution, not by this searcher.
 *
 * Labels are skipped here: [LuaLabelName] is already a `PsiNamedElement`, so the
 * default searcher drives its word search (see [LuaLabelReference.isReferenceTo]).
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
        parameters.optimizer.searchWord(
            name,
            parameters.effectiveSearchScope,
            true,
            target,
        )
    }

    /**
     * True when [element] is a Lua declaration IDENTIFIER leaf that
     * [LuaFindUsagesProvider.canFindUsagesFor] accepts (locals, parameters,
     * for-vars, local/global functions, methods) — but NOT a [LuaLabelName],
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
