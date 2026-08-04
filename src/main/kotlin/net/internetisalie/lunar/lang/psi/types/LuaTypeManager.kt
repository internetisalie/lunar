package net.internetisalie.lunar.lang.psi.types

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

interface LuaTypeManager {
    fun resolveType(name: String, context: PsiElement): LuaType?

    fun resolveModule(moduleName: String, context: PsiElement): LuaType?

    /**
     * BUG-395: resolves a free global name (`table`, `assert`, a project-wide `Lib = {}`) to the type
     * it was given in whichever file declares it — bundled stdlib stub, definition library, LuaRocks
     * tree or another project file.
     *
     * The type graph is built per file, so a global assigned elsewhere has no in-file binding and
     * inferred as nothing at all; this is the cross-file hook that gives it one. Returns null when
     * the name is not a known global, or its declaring file gives it no useful type.
     */
    fun resolveGlobal(name: String, context: PsiElement): LuaType?

    fun inferType(element: PsiElement): LuaType

    fun createTypeReference(name: String, context: PsiElement): LuaType

    companion object {
        fun getInstance(project: Project): LuaTypeManager = project.getService(LuaTypeManager::class.java)
    }
}
