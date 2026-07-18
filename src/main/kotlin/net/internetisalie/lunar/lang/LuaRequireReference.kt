package net.internetisalie.lunar.lang

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import net.internetisalie.lunar.lang.path.resolveModuleCandidates

class LuaRequireReference(
    element: PsiElement,
    textRange: TextRange,
    private val moduleName: String
) : PsiReferenceBase<PsiElement>(element, textRange) {

    // MAINT-30-03 (§2.5): the module→file resolution now lives in the single canonical
    // resolveModuleCandidates helper; this caller has no type gate, so it takes the first found file.
    override fun resolve(): PsiElement? =
        resolveModuleCandidates(element.project, moduleName).firstOrNull()
}
