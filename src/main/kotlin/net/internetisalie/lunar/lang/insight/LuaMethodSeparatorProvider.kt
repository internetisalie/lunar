package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl

/**
 * SYNTAX-05: draws a horizontal separator line above each function / method declaration,
 * honoring the IDE-wide "Show method separators" setting
 * (Settings | Editor | General | Appearance).
 *
 * Per the [LineMarkerProvider] contract, the marker is anchored on a leaf element — the keyword
 * leaf that begins the declaration — so we key off that leaf rather than the composite decl.
 */
class LuaMethodSeparatorProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (!DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS) return null
        if (element.firstChild != null) return null

        val declaration = element.parent
        if (declaration !is LuaFuncDecl && declaration !is LuaLocalFuncDecl) return null
        if (PsiTreeUtil.getDeepestFirst(declaration) !== element) return null
        if (!hasPrecedingCode(declaration)) return null

        val info = LineMarkerInfo(element, element.textRange)
        info.separatorColor = EditorColorsManager.getInstance().globalScheme
            .getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR)
        info.separatorPlacement = SeparatorPlacement.TOP
        return info
    }

    // Skip the separator above the first meaningful element so a stray line never lands at the top.
    private fun hasPrecedingCode(declaration: PsiElement): Boolean {
        var sibling = declaration.prevSibling
        while (sibling != null) {
            if (sibling !is PsiWhiteSpace && sibling !is PsiComment && sibling.textLength > 0) {
                return true
            }
            sibling = sibling.prevSibling
        }
        return false
    }
}
