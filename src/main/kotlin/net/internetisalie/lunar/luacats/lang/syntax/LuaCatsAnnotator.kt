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
            is LuaCatsLiteralType -> highlight(holder, element, LuaCatsHighlight.KEYWORD)
            is LuaCatsGenericType -> highlight(holder, element, LuaCatsHighlight.TYPE)
            is LuaCatsArgName -> highlight(holder, element, LuaCatsHighlight.NAME)
            is LuaCatsParameterName -> highlight(holder, element, LuaCatsHighlight.NAME)
            is LuaCatsFieldNameDescriptor -> highlight(holder, element, LuaCatsHighlight.NAME)
            is LuaCatsDeprecatedTag -> highlight(holder, element, LuaCatsHighlight.DEPRECATED)
            is LuaCatsArgValue -> highlight(holder, element, LuaCatsHighlight.VALUE)
            is LuaCatsArgSymbol -> highlight(holder, element, LuaCatsHighlight.SYMBOL)
            is LuaCatsDescription -> highlight(holder, element, LuaCatsHighlight.CONTENT)
            else -> {
                val et = element.elementType
                if (et == LuaCatsElementTypes.SYMBOL) {
                    val text = element.text
                    if (text == "(" || text == ")" || text == "[" || text == "]" || text == "{" || text == "}" || text == "<" || text == ">") {
                        highlight(holder, element, LuaCatsHighlight.BRACKETS)
                    } else {
                        highlight(holder, element, LuaCatsHighlight.SYMBOL)
                    }
                } else if (et == LuaCatsElementTypes.TAG) {
                    highlight(holder, element, LuaCatsHighlight.TAG)
                } else if (et == LuaCatsElementTypes.KEYWORD) {
                    highlight(holder, element, LuaCatsHighlight.KEYWORD)
                } else if (et == LuaCatsElementTypes.NAME) {
                    val parent = element.parent
                    if (parent is LuaCatsArgType && parent.parent is LuaCatsClassTag) {
                        highlight(holder, element, LuaCatsHighlight.NAME)
                    }
                }
            }
        }
    }
}
