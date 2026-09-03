package net.internetisalie.lunar.lang

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.search.GlobalSearchScope
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations

/**
 * A LuaCATS type-name **use**'s reference to its `@class`/`@alias` declaration
 * (`REFACT-08` design.md §2.3, §3.4, §3.5).
 *
 * [element] is the **holder** — a `LuaCatsNamedType`, `LuaCatsTypeParam` or `LuaCatsGenericType` —
 * never the NAME leaf inside it: each of those rules is `::= NAME` (`luacats.bnf:201-207`), so the
 * holder's own text is exactly the name and the whole element is the reference's range. The
 * rewrite still targets the leaf, through [LuaCatsTypeDeclarations.useLeafOf].
 *
 * Resolution is a plain [LuaCatsTypeDeclarations.declarationLeaves] lookup over
 * [GlobalSearchScope.allScope] — the same scope [net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl]
 * resolves a class name in, so navigation and type resolution cannot disagree about which
 * declarations exist. Reuses [net.internetisalie.lunar.lang.indexing.LuaCatsTypeNameIndex] via that
 * function; this class builds no second name-to-declaration map.
 */
class LuaCatsTypeReference(
    element: PsiElement,
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength)),
    PsiPolyVariantReference {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        declarations().map { PsiElementResolveResult(it) }.toTypedArray()

    override fun resolve(): PsiElement? = declarations().firstOrNull()

    /**
     * `O(1)`, and required to be — the searcher's inner loop calls this once per candidate use
     * holder, and re-resolving through the index there would make the search quadratic in project
     * size. Sound because a LuaCATS type name is project-global and unscoped: any declaration slot
     * spelling the same name IS this reference's target's peer.
     */
    override fun isReferenceTo(element: PsiElement): Boolean =
        element.text == this.element.text && LuaCatsTypeDeclarations.isDeclarationLeaf(element)

    /**
     * Rewrites the use's NAME leaf in place and returns the (unmoved) holder, matching
     * [net.internetisalie.lunar.refactoring.rename.LuaCatsParamRenamer]'s
     * [LeafElement.replaceWithText] idiom: it interns text and calls `replaceChild`, neither
     * parsing nor validating.
     *
     * Returns [element] unwritten — never throws — when [LuaCatsTypeDeclarations.useLeafOf] finds
     * no NAME leaf. The platform calls this once per usage inside one rename pass; throwing here
     * would abort a rename already half applied for a case [LuaCatsTypeReference.isReferenceTo]
     * already keeps out of the usage set in practice.
     */
    override fun handleElementRename(newElementName: String): PsiElement {
        val leaf = LuaCatsTypeDeclarations.useLeafOf(element) ?: return element
        val node = leaf.node as? LeafElement ?: return element
        node.replaceWithText(newElementName)
        return element
    }

    private fun declarations(): List<PsiElement> =
        LuaCatsTypeDeclarations.declarationLeaves(
            element.text,
            element.project,
            GlobalSearchScope.allScope(element.project),
        )
}
