package net.internetisalie.lunar.lang

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.LuaIcons.FILE
import net.internetisalie.lunar.lang.psi.LuaLabel
import net.internetisalie.lunar.lang.psi.LuaLabelRef

class LuaLabelReference(element: PsiElement, textRange: TextRange) :
    PsiReferenceBase<PsiElement?>(element, textRange), PsiPolyVariantReference {
    private val name = element.text.substring(textRange.startOffset, textRange.endOffset)

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val element = myElement ?: return emptyArray()
        // Label resolution: find labels with matching name in file
        val labels = findLabels(element.containingFile, name)
        if (labels.isEmpty()) return emptyArray()
        return labels.mapNotNull { label ->
            val target = label.labelName.identifier ?: label.labelName.firstChild
            if (target != null) PsiElementResolveResult(target) else null
        }.toTypedArray()
    }

    override fun resolve(): PsiElement? {
        val resolveResults = multiResolve(false)
        return if (resolveResults.size == 1) resolveResults[0].element else null
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        return (element is LuaLabelRef) &&
                element.identifier?.text == name &&
                resolve() === element
    }

    override fun getVariants(): Array<Any> {
        val containingFile = myElement!!.containingFile
        val labels = findLabels(containingFile, name)
        val variants: MutableList<LookupElement> = ArrayList()
        for (label in labels) {
            val identifier = label.labelName.identifier ?: label.labelName.firstChild
            if (identifier != null && identifier.text.isNotEmpty()) {
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
            val identifier = label.labelName.identifier ?: label.labelName.firstChild
            if (identifier?.text == name) {
                result.add(label)
            }
        }
        return result
    }
}
