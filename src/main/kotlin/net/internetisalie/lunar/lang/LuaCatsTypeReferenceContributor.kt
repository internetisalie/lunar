package net.internetisalie.lunar.lang

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsGenericType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsNamedType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeParam

/**
 * Attaches a [LuaCatsTypeReference] to each of the three LuaCATS type-name use holders
 * (`REFACT-08` design.md §2.4). Modelled on [LuaLabelReferenceContributor].
 *
 * **All three holders, not just [LuaCatsNamedType].** `REFACT-08-00-DR-01` measured that a
 * [LuaCatsNamedType]-only contributor misses 44 of the corpus's 1126 uses — the `LuaCatsTypeParam`
 * arguments of `table<K, V>` and the `LuaCatsGenericType` head of `Foo<…>`.
 *
 * **The gate is [LuaCatsTypeDeclarations.useLeafOf], and nothing here re-derives its
 * discrimination.** It already excludes a declaration slot holder (a parameterized class head or
 * its own parameters, a `@generic` parameter) and a shadowed type-parameter name, so there is
 * exactly one clause anywhere in this feature that can mis-classify a holder.
 */
class LuaCatsTypeReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val provider = TypeUseReferenceProvider()
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(LuaCatsNamedType::class.java), provider)
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(LuaCatsTypeParam::class.java), provider)
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(LuaCatsGenericType::class.java), provider)
    }

    private class TypeUseReferenceProvider : PsiReferenceProvider() {
        override fun getReferencesByElement(
            element: PsiElement,
            context: ProcessingContext,
        ): Array<PsiReference> =
            if (LuaCatsTypeDeclarations.useLeafOf(element) == null) {
                PsiReference.EMPTY_ARRAY
            } else {
                arrayOf(LuaCatsTypeReference(element))
            }
    }
}
