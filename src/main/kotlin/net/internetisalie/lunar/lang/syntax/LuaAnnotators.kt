package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.*

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
        val delimLen = getLuaStringDelimiterLength(text)
        if (delimLen < 2) return

        val textRange = element.textRange
        val beginRange = TextRange(textRange.startOffset, textRange.startOffset + delimLen)
        val endRange = TextRange(textRange.endOffset - delimLen, textRange.endOffset)

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
        val delimLen = getLuaCommentDelimiterLength(text)
        if (delimLen <= 2) return

        val textRange = element.textRange
        val beginRange = TextRange(textRange.startOffset, textRange.startOffset + delimLen)
        val endRange = TextRange(textRange.endOffset - (delimLen - 2), textRange.endOffset)

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
