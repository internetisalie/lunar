package net.internetisalie.lunar.lang.psi.types

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

interface LuaTypeManager {
    fun resolveType(name: String, context: PsiElement): LuaType?
    
    fun inferType(element: PsiElement): LuaType
    
    fun createTypeReference(name: String, context: PsiElement): LuaType

    companion object {
        fun getInstance(project: Project): LuaTypeManager = project.getService(LuaTypeManager::class.java)
    }
}
