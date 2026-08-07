package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaExprStatement

class LuaStandaloneExpressionAnnotator : Annotator {
    override fun annotate(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        if (element !is LuaExprStatement) return
        // The rule itself lives in LuaSyntaxDiagnostics: the MAINT-35 parse oracle has to ask the
        // same question, and a second inline copy of it would drift (BUG-409).
        if (!LuaSyntaxDiagnostics.isInvalidStatement(element)) return

        holder
            .newAnnotation(HighlightSeverity.ERROR, "Expression cannot be used as a statement")
            .range(element)
            .create()
    }
}
