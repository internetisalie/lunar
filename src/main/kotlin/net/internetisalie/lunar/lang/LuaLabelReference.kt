package net.internetisalie.lunar.lang

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.ResolveState
import net.internetisalie.lunar.lang.LuaIcons.FILE
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaGlobalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.processLabelDeclarations

class LuaLabelReference(element: PsiElement, textRange: TextRange) :
    PsiReferenceBase<PsiElement?>(element, textRange), PsiPolyVariantReference {
    private val name = element.text.substring(textRange.startOffset, textRange.endOffset)

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val target = resolveLabel() ?: return ResolveResult.EMPTY_ARRAY
        return arrayOf(PsiElementResolveResult(target))
    }

    override fun resolve(): PsiElement? = resolveLabel()

    private fun resolveLabel(): LuaLabelName? {
        val ref = myElement ?: return null
        val processor = LuaLabelScopeProcessor(name)
        walkLabelScopes(ref) { block ->
            block.processLabelDeclarations(processor, ResolveState.initial())
        }
        return processor.result
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val ref = myElement as? PsiNamedElement ?: return myElement ?: error("no element")
        return ref.setName(newElementName)
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val resolved = resolveLabel() ?: return false
        val owner = (element as? LuaLabelName)
            ?: (element.parent as? LuaLabelName) // tolerate IDENTIFIER leaf targets
        return resolved === owner && resolved.identifier.text == name
    }

    override fun getVariants(): Array<Any> {
        val ref = myElement ?: return emptyArray()
        val processor = LuaLabelCompletionScopeProcessor()
        walkLabelScopes(ref) { block ->
            block.processLabelDeclarations(processor, ResolveState.initial())
        }
        return processor.results.values
            .map { LookupElementBuilder.create(it.identifier.text).withIcon(FILE) }
            .toTypedArray()
    }

    private fun walkLabelScopes(start: PsiElement, visit: (LuaBlock) -> Boolean) {
        var current: PsiElement? = start
        while (current != null && current !is PsiFile) {
            if (current is LuaBlock) {
                if (!visit(current)) {
                    return
                }
            }
            if (current is LuaFuncDef ||
                current is LuaFuncDecl ||
                current is LuaLocalFuncDecl ||
                current is LuaGlobalFuncDecl
            ) {
                return
            }
            current = current.parent
        }
    }
}
