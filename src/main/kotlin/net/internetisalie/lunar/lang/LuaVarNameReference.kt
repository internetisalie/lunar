package net.internetisalie.lunar.lang

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.LuaIcons.FILE
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.syntax.LuaBindingsVisitor

class LuaVarNameReference(element: PsiElement, textRange: TextRange) :
    PsiReferenceBase<PsiElement?>(element, textRange), PsiPolyVariantReference {
    private val name = element.text.substring(textRange.startOffset, textRange.endOffset)

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val element = myElement ?: return emptyArray()
        val references = LuaBindingsVisitor.getReferences(element)
        val reference = references[element.textOffset] ?: return emptyArray()
        if (!reference.defined()) return emptyArray()
        return arrayOf(PsiElementResolveResult(reference.binding!!.element))
    }

    override fun resolve(): PsiElement? {
        val resolveResults = multiResolve(false)
        return if (resolveResults.size == 1) resolveResults[0].element else null
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        return element.elementType == LuaElementTypes.IDENTIFIER &&
                element.text == name &&
                resolve() === element
    }

    override fun getVariants(): Array<Any> {
        val element = myElement ?: return emptyArray()
        val references = LuaBindingsVisitor.getReferences(element)
        val reference = references[element.textOffset] ?: return emptyArray()
        if (!reference.defined()) return emptyArray()
        return arrayOf(
            LookupElementBuilder
                .create(reference.binding!!.element)
                .withIcon(FILE)
                .withTypeText(element.containingFile.name)
        )
    }
}