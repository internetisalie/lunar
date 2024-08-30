package net.internetisalie.lunar.lang

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import net.internetisalie.lunar.lang.LuaIcons.FILE
import net.internetisalie.lunar.lang.psi.LuaLabelRef

class LuaLabelReference(element: PsiElement, textRange: TextRange) :
    PsiReferenceBase<PsiElement?>(element, textRange), PsiPolyVariantReference {
    private val name = element.text.substring(textRange.startOffset, textRange.endOffset)

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val containingFile = myElement!!.containingFile
        val labels = LuaLabelUtil.findLabels(containingFile, name)
        val results: MutableList<ResolveResult> = ArrayList()
        for (label in labels) {
            results.add(PsiElementResolveResult(label))
        }
        return results.toTypedArray<ResolveResult>()
    }

    override fun resolve(): PsiElement? {
        val resolveResults = multiResolve(false)
        return if (resolveResults.size == 1) resolveResults[0].element else null
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        return (element is LuaLabelRef) &&
                element.identifier.text == name &&
                resolve() === element
    }

    override fun getVariants(): Array<Any> {
        val containingFile = myElement!!.containingFile
        val labels = LuaLabelUtil.findLabels(containingFile, name)
        val variants: MutableList<LookupElement> = ArrayList()
        for (label in labels) {
            if (!label.labelName.identifier.text.isEmpty()) {
                variants.add(
                    LookupElementBuilder
                        .create(label)
                        .withIcon(FILE)
                        .withTypeText(label.containingFile.name)
                )
            }
        }
        return variants.toTypedArray()
    }
}