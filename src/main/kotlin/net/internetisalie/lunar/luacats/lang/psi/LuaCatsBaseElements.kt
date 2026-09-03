package net.internetisalie.lunar.luacats.lang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry

interface LuaCatsCommentOwner : PsiElement {
    val catsComment: LuaCatsComment?
}

/**
 * `REFACT-08-14`, the change without which no LuaCATS `psi.referenceContributor` reaches any
 * LuaCATS element. The platform default (`PsiElementBase.getReferences()` →
 * `SharedPsiElementImplUtil.getReferences`) wraps `getReference()` and never consults
 * [ReferenceProvidersRegistry] — measured, not read (`REFACT-08` design.md §1.1 F3): a contributor
 * registered against a LuaCATS use shape is a legal registration and is inert without this override.
 * [net.internetisalie.lunar.lang.psi.LuaBaseElement] already carries the Lua-side twin.
 *
 * Both overrides are load-bearing on their own. [getReferences] alone leaves every consumer that
 * reads `element.reference` — including `substituteElementToRename` — seeing null even though a
 * reference exists (`REFACT-08-00-DR-02` P3, the TC-3 negative control).
 *
 * **Must NOT merge [getReference] into [getReferences] the way `LuaBaseElement` does** — that
 * class's `getReference()` is an independent override on a different subtype; here the two would
 * recurse.
 */
open class LuaCatsBaseElement(
    node: ASTNode,
) : ASTWrapperPsiElement(node) {
    override fun toString(): String = this.node.elementType.toString()

    override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)

    override fun getReference(): PsiReference? = references.firstOrNull()
}
