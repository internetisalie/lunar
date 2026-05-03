package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.insight.Kind
import net.internetisalie.lunar.lang.insight.LuaBindingsVisitor
import net.internetisalie.lunar.lang.insight.Reference
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.psi.LuaAttribName
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
//        holder.newSilentAnnotation(severity)
//            .range(target)
//            .textAttributes(highlight)
//            .create()
    }
}

class LuaNumeralAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.elementType != LuaElementTypes.NUMBER) return
        val text = element.text
        if (text.startsWith("0x", ignoreCase = true)) {
            annotateHexNumeral(text, element, holder)
        } else {
            annotateDecimalNumeral(text, element, holder)
        }
        annotateSemanticKind(text, element, holder)
    }

    private fun annotateDecimalNumeral(text: String, element: PsiElement, holder: AnnotationHolder) {
        val expIdx = text.indexOfFirst { it == 'e' || it == 'E' }
        if (expIdx == -1) return
        val afterSign = text.substring(expIdx + 1).trimStart('+', '-')
        if (afterSign.isEmpty()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Malformed number: exponent has no digits")
                .range(element)
                .create()
        }
    }

    private fun annotateHexNumeral(text: String, element: PsiElement, holder: AnnotationHolder) {
        val afterPrefix = text.substring(2)
        val expIdx = afterPrefix.indexOfFirst { it == 'p' || it == 'P' }
        if (expIdx == -1) {
            if (afterPrefix.filter { it in "0123456789abcdefABCDEF." }.isEmpty()) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Malformed number: expected hexadecimal digit after '0x'")
                    .range(element)
                    .create()
            }
            return
        }
        val hexPart = afterPrefix.substring(0, expIdx).filter { it in "0123456789abcdefABCDEF" }
        if (hexPart.isEmpty()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Malformed number: expected hexadecimal digit before exponent")
                .range(element)
                .create()
        }
        val afterSign = afterPrefix.substring(expIdx + 1).trimStart('+', '-')
        if (afterSign.isEmpty()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Malformed number: exponent has no digits")
                .range(element)
                .create()
        }
    }

    private fun annotateSemanticKind(text: String, element: PsiElement, holder: AnnotationHolder) {
        val isFloat = text.contains('.') ||
            text.contains('e', ignoreCase = true) && !text.startsWith("0x", ignoreCase = true) ||
            text.contains('p', ignoreCase = true) && text.startsWith("0x", ignoreCase = true)
        val key = if (isFloat) LuaHighlight.NUMBER_FLOAT else LuaHighlight.NUMBER_INT
        holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
            .range(element)
            .textAttributes(key)
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

class LuaAttribNameAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is LuaAttribName -> annotateAttribName(element, holder)
            is LuaLocalVarDecl -> annotateLocalVarDecl(element, holder)
        }
    }

    private fun annotateAttribName(
        target: LuaAttribName,
        holder: AnnotationHolder
    ) {
        holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
            .range(target)
            .textAttributes(LuaHighlight.ATTRIB_NAME)
            .create()
    }

    private fun annotateLocalVarDecl(
        target: LuaLocalVarDecl,
        holder: AnnotationHolder
    ) {
        val hasAttribute = target.attNameList.any { it.attrib != null }
        if (hasAttribute && target.exprList == null) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Local variables with attributes must be initialized")
                .range(target)
                .create()
        }
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
            if (reference.name.size == 1 && reference.kind != Kind.Label) {
                LuaBindingsAnnotator.undefinedReference(holder, target, reference, referenceSource)
            }
            return
        }

        val binding = reference.binding!!

        if (binding.kind == Kind.Label && binding.shadowed) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Duplicate label '${referenceSource.text}'")
                .range(target)
                .create()
        }

        val highlight = when {
            binding.global && binding.kind == Kind.Function -> LuaHighlight.CALL_GLOBAL
            binding.global -> LuaHighlight.VAR_GLOBAL
            binding.shadowed && binding.kind != Kind.Label -> LuaHighlight.VAR_SHADOWED
            binding.kind == Kind.Label -> LuaHighlight.LABEL
            binding.kind == Kind.Function -> LuaHighlight.CALL_LOCAL
            binding.param -> LuaHighlight.PARAMETER
            reference.upValue -> LuaHighlight.VAR_UP_VALUE
            else -> LuaHighlight.VAR_LOCAL
        }

        LuaBindingsAnnotator.identifier(holder, HighlightSeverity.INFORMATION, target, reference.toString(), highlight)

        // Check for constant assignment
        if (target.parent is LuaNameRef && target.parent.parent is LuaVar && target.parent.parent.parent is LuaVarList) {
            val bindingElement = binding.element
            val attName = PsiTreeUtil.getParentOfType(bindingElement, LuaAttName::class.java)
            if (attName?.attrib?.attribName?.text == "const") {
                holder.newAnnotation(HighlightSeverity.ERROR, "Cannot assign to a constant variable: ${target.text}")
                    .range(target)
                    .create()
            }
        }
    }
}

class LuaGotoAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is LuaGotoStatement) return
        val labelRef = element.labelRef
        val identifier = labelRef.identifier
        val reference = LuaBindingsVisitor.getBindings(identifier).lookup(identifier) ?: return

        if (!reference.defined) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Unresolved label '${identifier.text}'")
                .range(identifier)
                .create()
            return
        }

        val binding = reference.binding!!
        val labelElement = binding.element.parent.parent // LuaLabelName -> LuaLabel
        if (labelElement !is LuaLabel) return

        // Check for jumping into scope of local variables
        // A jump forward in the same block is not allowed if it jumps over a declaration of a local variable.
        if (labelElement.textOffset > element.textOffset) {
            // Forward jump
            val commonBlock = PsiTreeUtil.findCommonParent(element, labelElement)
            if (commonBlock is LuaBlock) {
                var current = element.nextSibling
                while (current != null && current != labelElement) {
                    if (current is LuaLocalVarDecl || current is LuaLocalFuncDecl) {
                        holder.newAnnotation(HighlightSeverity.ERROR, "Cannot jump into the scope of local variable")
                            .range(identifier)
                            .create()
                        break
                    }
                    current = current.nextSibling
                }
            } else {
                // Label is in a nested block? (should have been unresolved if scoping is correct)
                // Actually, if resolution succeeded, it means the label IS visible.
                // If it's visible but in a nested block, it MUST be a jump into scope.
                val labelBlock = PsiTreeUtil.getParentOfType(labelElement, LuaBlock::class.java)
                val gotoBlock = PsiTreeUtil.getParentOfType(element, LuaBlock::class.java)
                if (labelBlock != null && gotoBlock != null && labelBlock != gotoBlock && PsiTreeUtil.isAncestor(gotoBlock, labelBlock, true)) {
                    holder.newAnnotation(HighlightSeverity.ERROR, "Cannot jump into the scope of local variable")
                        .range(identifier)
                        .create()
                }
            }
        }
    }
}

class LuaGlobalBindingsAnnotator: Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element.elementType) {
            LuaElementTypes.IDENTIFIER -> annotateElement(element, element, holder)
            LuaElementTypes.STRING -> annotateString(element, holder)
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
        val bindings = LuaBindingsVisitor.getBindingsWithImports(referenceSource)
        return bindings.lookup(referenceSource)
    }
}
