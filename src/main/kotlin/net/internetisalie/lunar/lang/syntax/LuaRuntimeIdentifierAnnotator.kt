package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.project.PlatformLibraryIndex

class LuaRuntimeIdentifierAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is LuaVar) return
        val identifier = element.varName?.identifier ?: return
        val name = identifier.text ?: return
        val range = identifier.textRange

        val platformPackages = PlatformLibraryIndex.instance.getPackageNames()
        if (platformPackages.contains(name)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(LuaHighlight.PACKAGE)
                .create()
        }

        val platformGlobals = PlatformLibraryIndex.instance.getGlobalNames()
        if (platformGlobals.contains(name)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(LuaHighlight.BUILTIN)
                .create()

        }
    }
}
