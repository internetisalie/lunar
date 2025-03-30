package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLabel

class LuaLocalBindingsAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when {
            element is LuaLabel -> annotateElement(element, element.labelName.identifier, holder)
            element.elementType == LuaElementTypes.IDENTIFIER -> annotateElement(element, element, holder)
        }
    }

    private fun annotateElement(target: PsiElement, referenceSource: PsiElement, holder: AnnotationHolder) {
        val reference = LuaBindingsVisitor.getBindings(referenceSource).lookup(referenceSource) ?: return
        if (reference.global) return // Only annotate local references

        if (!reference.defined) {
            if (reference.name.size == 1) {
                LuaBindingsAnnotator.undefinedReference(holder, target, reference, referenceSource)
            }
            return
        }

        val binding = reference.binding!!

        val highlight = when {
            binding.global && binding.kind == Kind.Function -> LuaHighlight.CALL_GLOBAL
            binding.global -> LuaHighlight.VAR_GLOBAL
            binding.shadowed -> LuaHighlight.VAR_SHADOWED
            binding.kind == Kind.Label -> LuaHighlight.LABEL
            binding.kind == Kind.Function -> LuaHighlight.CALL_LOCAL
            binding.param -> LuaHighlight.PARAMETER
            else -> LuaHighlight.VAR_LOCAL
        }

        LuaBindingsAnnotator.identifier(holder, HighlightSeverity.INFORMATION, target, reference.toString(), highlight)
    }

}
