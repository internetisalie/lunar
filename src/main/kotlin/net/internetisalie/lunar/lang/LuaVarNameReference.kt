package net.internetisalie.lunar.lang

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import net.internetisalie.lunar.lang.LuaIcons.FILE
import net.internetisalie.lunar.lang.psi.LuaNameDecl
import net.internetisalie.lunar.lang.psi.LuaVarName

class LuaVarNameReference(element: PsiElement, textRange: TextRange) :
    PsiReferenceBase<PsiElement?>(element, textRange), PsiPolyVariantReference {
    private val name = element.text.substring(textRange.startOffset, textRange.endOffset)

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val vars = LuaNameUtil.findVars(element, name)
        val results: MutableList<ResolveResult> = ArrayList()
        for (var_ in vars) {
            results.add(PsiElementResolveResult(var_))
        }
        return results.toTypedArray<ResolveResult>()
    }

    override fun resolve(): PsiElement? {
        val resolveResults = multiResolve(false)
        return if (resolveResults.size == 1) resolveResults[0].element else null
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        return (element is LuaVarName) &&
                element.identifier.text == name &&
                resolve() === element
    }

    override fun getVariants(): Array<Any> {
        val containingFile = myElement!!.containingFile
        val foundVars = LuaNameUtil.findVars(element, name)
        val variants: MutableList<LookupElement> = ArrayList()
        for (foundVar in foundVars) {
            if (foundVar is LuaNameDecl && foundVar.identifier.text.isNotEmpty()) {
                variants.add(
                    LookupElementBuilder
                        .create(foundVar)
                        .withIcon(FILE)
                        .withTypeText(containingFile.name)
                )
            } else if (foundVar is LuaVarName &&  foundVar.identifier.text.isNotEmpty()) {
                variants.add(
                    LookupElementBuilder
                    .create(foundVar)
                    .withIcon(FILE)
                    .withTypeText(containingFile.name)
                )
            }
        }
        return variants.toTypedArray()
    }
}