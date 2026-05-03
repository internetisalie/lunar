package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaExprStatement
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaPrefixExpr

class LuaStandaloneExpressionAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is LuaExprStatement) return

        val expr = element.expr
        if (expr is LuaFuncCall) {
            return
        }

        holder.newAnnotation(HighlightSeverity.ERROR, "Expression cannot be used as a statement")
            .range(element)
            .create()
    }
}
