package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLabel

class LuaBindingsAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when {
            element is LuaLabel -> annotateElement(element, element.labelName.identifier, holder)
            element.elementType == LuaElementTypes.IDENTIFIER -> annotateElement(element, element, holder)
        }
    }

    private fun annotateElement(target: PsiElement, referenceSource: PsiElement, holder: AnnotationHolder) {
        val references = LuaBindingsVisitor.getReferences(referenceSource)
        val reference = references[referenceSource.textOffset] ?: return

        if (!reference.defined()) {
            val message = if (reference.kind != null) "${reference.kind} not defined" else "Name not defined"
            holder.newAnnotation(HighlightSeverity.ERROR, message)
                .range(target)
                .textAttributes(LuaHighlight.REF_UNDEFINED)
                .create()
            return
        }

        val binding = reference.binding!!

        val highlight = when {
            binding.platform && binding.kind == Kind.Function -> LuaHighlight.CALL_PLATFORM
            binding.platform && binding.kind == Kind.Package -> LuaHighlight.PACKAGE
            binding.platform -> LuaHighlight.REF_PLATFORM
            binding.global && binding.kind == Kind.Function -> LuaHighlight.CALL_GLOBAL
            binding.global -> LuaHighlight.REF_GLOBAL
            binding.shadowed -> LuaHighlight.REF_SHADOWED
            binding.kind == Kind.Label -> LuaHighlight.LABEL
            else -> LuaHighlight.REF_LOCAL
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(target)
            .textAttributes(highlight)
            .create()
    }
}
