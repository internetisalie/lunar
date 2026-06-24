package net.internetisalie.lunar.lang.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaMemberFieldIndex
import net.internetisalie.lunar.lang.indexing.dottedMemberName
import net.internetisalie.lunar.lang.indexing.memberFieldIdentifier
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaFile

/**
 * Resolves a qualified member-field name (`receiver.field`) to its declaration identifier(s)
 * (NAV-12). Backed by [LuaMemberFieldIndex]; re-resolves the matching assignment-target field in each
 * containing file. Shared by [net.internetisalie.lunar.lang.LuaNameReference] (Go-to / Find Usages)
 * and the documentation provider so resolution and quick-doc agree.
 */
object LuaMemberFieldNavigation {

    fun find(project: Project, qualifiedName: String, scope: GlobalSearchScope): List<PsiElement> {
        val index = FileBasedIndex.getInstance()
        val psiManager = PsiManager.getInstance(project)
        val results = mutableListOf<PsiElement>()
        for (virtualFile in index.getContainingFiles(LuaMemberFieldIndex.KEY, qualifiedName, scope)) {
            val luaFile = psiManager.findFile(virtualFile) as? LuaFile ?: continue
            for (stmt in PsiTreeUtil.findChildrenOfType(luaFile, LuaAssignmentStatement::class.java)) {
                for (target in stmt.varList.varList) {
                    if (dottedMemberName(target) != qualifiedName) continue
                    memberFieldIdentifier(target)?.let { results.add(it) }
                }
            }
        }
        return results
    }
}
