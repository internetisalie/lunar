package net.internetisalie.lunar.lang

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import net.internetisalie.lunar.lang.psi.LuaLabelRef

class LuaLabelReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LuaLabelRef::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val labelRef = element as LuaLabelRef
                    val value = labelRef.identifier?.text ?: labelRef.firstChild?.text
                    if (value != null) {
                        val label = TextRange(0, value.length)
                        return arrayOf(LuaLabelReference(element, label))
                    }
                    return PsiReference.EMPTY_ARRAY
                }
            })
    }
}
