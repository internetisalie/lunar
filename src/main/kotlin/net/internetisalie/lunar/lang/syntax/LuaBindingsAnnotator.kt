package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement

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