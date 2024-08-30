package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaLabel
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLabelRef

class LuaLabelAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is LuaLabelRef) {
            annotateIdentifier(element, holder)
        } else if (element is LuaLabel) {
            annotateIdentifier(element, holder)
        }
    }

    private fun annotateIdentifier(element: PsiElement, holder: AnnotationHolder) {
        val range = element.textRange
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(range)
            .textAttributes(LuaHighlight.LABEL)
            .create();
    }
}
