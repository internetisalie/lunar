package net.internetisalie.lunar.lang.doc

import com.intellij.navigation.NavigationItem
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement

/** Public-API stand-in for the platform's internal `com.intellij.codeInsight.navigation.targetPresentation(PsiElement)`. */
internal object LuaTargetPresentation {
    fun of(element: PsiElement): TargetPresentation {
        val presentableText =
            (element as? NavigationItem)?.presentation?.presentableText
                ?: (element as? PsiNamedElement)?.name
                ?: element.text
                ?: element.toString()
        val virtualFile = element.containingFile?.virtualFile
        return TargetPresentation
            .builder(presentableText)
            .icon(element.getIcon(Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS))
            .backgroundColor(virtualFile?.let { VfsPresentationUtil.getFileBackgroundColor(element.project, it) })
            .presentation()
    }
}
