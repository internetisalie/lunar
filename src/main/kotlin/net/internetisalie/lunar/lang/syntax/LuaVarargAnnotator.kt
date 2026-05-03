package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.*

class LuaVarargAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.elementType != LuaElementTypes.ELLIPSIS) return
        if (element.parent is LuaParList) return // vararg parameter definition

        // Find enclosing function or file
        val context = PsiTreeUtil.getParentOfType(
            element,
            LuaFuncDef::class.java,
            LuaFuncDecl::class.java,
            LuaLocalFuncDecl::class.java,
            LuaFile::class.java
        )

        if (context is LuaFile) return

        val parList = when (context) {
            is LuaFuncDef -> context.parList
            is LuaFuncDecl -> context.parList
            is LuaLocalFuncDecl -> context.parList
            else -> null
        }

        if (parList == null || parList.node.findChildByType(LuaElementTypes.ELLIPSIS) == null) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Cannot use '...' outside a vararg function")
                .range(element)
                .create()
        }
    }
}
