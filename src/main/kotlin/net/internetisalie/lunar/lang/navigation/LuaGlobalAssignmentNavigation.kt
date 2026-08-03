package net.internetisalie.lunar.lang.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaFile

/**
 * Resolves a bare global name to the assignment(s) that declare it, anywhere in the project
 * (BUG-391). Backed by [LuaGlobalAssignmentIndex]; re-resolves the target identifier in each
 * containing file, mirroring [LuaMemberFieldNavigation] for the dotted case.
 */
object LuaGlobalAssignmentNavigation {

    fun find(project: Project, name: String, scope: GlobalSearchScope): List<PsiElement> {
        val index = FileBasedIndex.getInstance()
        val psiManager = PsiManager.getInstance(project)
        val results = mutableListOf<PsiElement>()
        for (virtualFile in index.getContainingFiles(LuaGlobalAssignmentIndex.KEY, name, scope)) {
            val luaFile = psiManager.findFile(virtualFile) as? LuaFile ?: continue
            // File-scope statements only, matching what the indexer recorded.
            luaFile.getBlockList()
                .flatMap { it.statementList }
                .filterIsInstance<LuaAssignmentStatement>()
                .forEach { stmt -> collectTargets(stmt, name, results) }
        }
        return results
    }

    private fun collectTargets(stmt: LuaAssignmentStatement, name: String, into: MutableList<PsiElement>) {
        stmt.varList.varList.forEach { target ->
            if (target.varSuffixList.isEmpty() && target.nameRef?.text == name) {
                target.nameRef?.identifier?.let { into.add(it) }
            }
        }
    }
}
