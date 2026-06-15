package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor

object LuaResolveUtil {
    fun scopeCrawlUp(processor: PsiScopeProcessor, element: PsiElement): Boolean {
        var prev: PsiElement? = null
        var current: PsiElement? = element

        while (current != null && current !is PsiFile) {
            val state = ResolveState.initial()
            val matchFound = when (current) {
                is LuaBlock -> !current.processDeclarations(processor, state, prev, element)
                is LuaFuncDef -> !current.processDeclarations(processor, state, prev, element)
                is LuaFuncDecl -> !current.processDeclarations(processor, state, prev, element)
                is LuaLocalFuncDecl -> !current.processDeclarations(processor, state, prev, element)
                is LuaNumericForStatement -> !current.processDeclarations(processor, state, prev ?: element, element)
                is LuaGenericForStatement -> !current.processDeclarations(processor, state, prev ?: element, element)
                else -> false
            }

            if (matchFound) {
                return false
            }

            prev = current
            current = current.parent
        }

        if (current is LuaFile) {
            val state = ResolveState.initial()
            if (!current.processDeclarations(processor, state, prev, element)) {
                return false
            }
        }

        return true
    }
}
