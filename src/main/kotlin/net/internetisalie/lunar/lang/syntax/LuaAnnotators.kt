package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLabel


object LuaBindingsAnnotator {
    fun undefinedReference(
        holder: AnnotationHolder,
        target: PsiElement,
        reference: Reference,
        referenceSource: PsiElement
    ) {
        val message =
            if (reference.kind != null)
                "${reference.kind} not defined: ${referenceSource.text}"
            else
                "Name not defined: ${referenceSource.text}"
        holder.newAnnotation(HighlightSeverity.ERROR, message)
            .range(target)
            .textAttributes(LuaHighlight.REF_UNDEFINED)
            .create()
        return
    }

    fun identifier(
        holder: AnnotationHolder,
        severity: HighlightSeverity,
        target: PsiElement,
        message: String,
        highlight: TextAttributesKey
    ) {
        holder.newAnnotation(severity, message)
            .range(target)
            .tooltip(message)
            .textAttributes(highlight)
            .create()

    }
}

class LuaLongStringAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.elementType != LuaElementTypes.STRING) return

        val text = element.text
        if (text[0] != '[') { return }
        var level = 0
        while (text[level+1] == '=') level++

        val textRange = element.textRange
        val beginRange = TextRange(textRange.startOffset, textRange.startOffset + level + 2)
        val endRange = TextRange(textRange.endOffset - level - 2, textRange.endOffset)

        holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
            .range(beginRange)
            .textAttributes(LuaHighlight.LONGSTRING_BRACES)
            .create()
        holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
            .range(endRange)
            .textAttributes(LuaHighlight.LONGSTRING_BRACES)
            .create()
    }
}

class LuaLongCommentAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.elementType != LuaElementTypes.LONGCOMMENT) return

        val text = element.text
        if (text.substring(0, 3) != "--[") { return }
        var level = 0
        while (text[level+3] == '=') level++

        val textRange = element.textRange
        val beginRange = TextRange(textRange.startOffset, textRange.startOffset + level + 4)
        val endRange = TextRange(textRange.endOffset - level - 2, textRange.endOffset)

        holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
            .range(beginRange)
            .textAttributes(LuaHighlight.LONGCOMMENT_BRACES)
            .create()
        holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
            .range(endRange)
            .textAttributes(LuaHighlight.LONGCOMMENT_BRACES)
            .create()
    }
}

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
            reference.upValue -> LuaHighlight.VAR_UP_VALUE
            else -> LuaHighlight.VAR_LOCAL
        }

        LuaBindingsAnnotator.identifier(holder, HighlightSeverity.INFORMATION, target, reference.toString(), highlight)
    }

}

class LuaGlobalBindingsAnnotator: Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when {
            element.elementType == LuaElementTypes.IDENTIFIER -> annotateElement(element, element, holder)
            element.elementType == LuaElementTypes.STRING -> annotateString(element, holder)
        }
    }

    private fun annotateElement(target: PsiElement, referenceSource: PsiElement, holder: AnnotationHolder) {
        val reference = findReference(referenceSource) ?: return
        if (!reference.global) return // only annotate global references

        if (!reference.defined) {
            if (reference.name.size == 1) {
                LuaBindingsAnnotator.undefinedReference(holder, target, reference, referenceSource)
            }
            return
        }

        val binding = reference.binding!!
        val highlight = when {
            binding.platform && binding.kind == Kind.Function -> LuaHighlight.CALL_PLATFORM
            binding.platform && binding.kind == Kind.Package -> LuaHighlight.PACKAGE
            binding.platform -> LuaHighlight.VAR_PLATFORM
            binding.global && binding.kind == Kind.Function -> LuaHighlight.CALL_GLOBAL
            binding.global -> LuaHighlight.VAR_GLOBAL
            binding.kind == Kind.Function -> LuaHighlight.CALL_GLOBAL
            else -> LuaHighlight.VAR_GLOBAL
        }

        LuaBindingsAnnotator.identifier(holder, HighlightSeverity.INFORMATION, target, reference.toString(), highlight)
    }

    private fun  annotateString(target: PsiElement, holder: AnnotationHolder) {
        val reference = findReference(target) ?: return
        if (!reference.defined) {
            return
        }

        val binding = reference.binding!!
        val highlight = when {
            binding.kind == Kind.Package -> LuaHighlight.PACKAGE
            else -> return
        }

        LuaBindingsAnnotator.identifier(holder, HighlightSeverity.INFORMATION, target, reference.toString(), highlight)
    }

    private fun findReference(referenceSource : PsiElement) : Reference? {
        var bindings = LuaBindingsVisitor.getBindings(referenceSource)
        val imports = LuaImports.create(referenceSource.project, bindings)
        bindings = LuaBindingsVisitor.getBindingsWithImports(referenceSource, imports)
        return bindings.lookup(referenceSource)
    }
}