package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaVar

class LuaRuntimeIdentifierAnnotator : Annotator {
    // TODO: Add project runtime setting
    val RUNTIME_IDENTIFIERS = listOf(
        "require",
    )

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is LuaVar) return
        val identifier = element.identifier ?: return
        val name = identifier.text ?: return
        if (!RUNTIME_IDENTIFIERS.contains(name)) return
        val range = identifier.textRange

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(range)
            .textAttributes(DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL)
            .create();
    }
}
