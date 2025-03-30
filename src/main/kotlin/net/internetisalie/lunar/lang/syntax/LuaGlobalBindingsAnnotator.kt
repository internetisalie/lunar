package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes

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