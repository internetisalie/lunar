package net.internetisalie.lunar.luacats.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.luacats.lang.lexer.LuaCatsTokenTypes
import net.internetisalie.lunar.luacats.lang.psi.*

class LuaCatsAnnotator : Annotator {
    private fun highlight(holder: AnnotationHolder, element : PsiElement, attributes : TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element.textRange)
            .textAttributes(attributes)
            .create()
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is LuaCatsArgKeyword -> highlight(holder, element, LuaCatsHighlight.KEYWORD)
            is LuaCatsNamedType -> highlight(holder, element, LuaCatsHighlight.TYPE)
            is LuaCatsTypeParam -> highlight(holder, element, LuaCatsHighlight.TYPE)
            is LuaCatsBuiltinType -> highlight(holder, element, LuaCatsHighlight.TYPE)
            is LuaCatsGenericType -> highlight(holder, element, LuaCatsHighlight.TYPE)
            is LuaCatsArgName -> highlight(holder, element, LuaCatsHighlight.NAME)
            is LuaCatsArgValue -> highlight(holder, element, LuaCatsHighlight.VALUE)
            is LuaCatsArgSymbol -> highlight(holder, element, LuaCatsHighlight.SYMBOL)
            is LuaCatsDescription -> highlight(holder, element, LuaCatsHighlight.CONTENT)
            else -> {
                if (element.elementType == LuaCatsTokenTypes.LCATS_SYMBOL) {
                    highlight(holder, element, LuaCatsHighlight.SYMBOL)
                }
            }
        }
    }
}
