package net.internetisalie.lunar.lang

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.LuaIcons.FILE
import net.internetisalie.lunar.lang.psi.LuaLabel
import net.internetisalie.lunar.lang.psi.LuaLabelRef
import net.internetisalie.lunar.lang.insight.LuaBindingsVisitor

class LuaLabelReference(element: PsiElement, textRange: TextRange) :
    PsiReferenceBase<PsiElement?>(element, textRange), PsiPolyVariantReference {
    private val name = element.text.substring(textRange.startOffset, textRange.endOffset)

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val element = myElement ?: return emptyArray()
        val bindings = LuaBindingsVisitor.getBindings(element)
        val reference = bindings.references[element.textOffset] ?: return emptyArray()
        val binding = reference.binding ?: return emptyArray()
        return arrayOf(PsiElementResolveResult(binding.element))
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
        val labels = findLabels(containingFile, name)
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

    private fun findLabels(containingFile: PsiFile?, name: String?): MutableList<LuaLabel> {
        val result: MutableList<LuaLabel> = ArrayList()
        val labels = PsiTreeUtil.findChildrenOfType(containingFile, LuaLabel::class.java)
        for (label in labels) {
            if (label.getLabelName().getIdentifier().getText() == name) {
                result.add(label)
            }
        }
        return result
    }
}