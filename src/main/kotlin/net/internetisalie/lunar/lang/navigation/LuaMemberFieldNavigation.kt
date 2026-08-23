package net.internetisalie.lunar.lang.navigation

import com.intellij.openapi.progress.ProgressManager
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
    fun find(
        project: Project,
        qualifiedName: String,
        scope: GlobalSearchScope,
    ): List<PsiElement> {
        val index = FileBasedIndex.getInstance()
        val psiManager = PsiManager.getInstance(project)
        val results = mutableListOf<PsiElement>()
        for (virtualFile in index.getContainingFiles(LuaMemberFieldIndex.KEY, qualifiedName, scope)) {
            // REFACT-01 Phase 4 added a rename-time caller (LuaRenameConflictDetector's C3/C4
            // candidate set), which is exactly when a user cancels. Guarded at the two levels whose
            // bodies can force work: this one parses a file, and the next walks all of its
            // statements. The innermost loop over one statement's targets is left unguarded — the
            // AST is already in hand there, so a check would dilute the signal without bounding
            // anything. Same two levels, and the same reasoning, as LuaGlobalAssignmentNavigation.
            ProgressManager.checkCanceled()
            val luaFile = psiManager.findFile(virtualFile) as? LuaFile ?: continue
            for (stmt in PsiTreeUtil.findChildrenOfType(luaFile, LuaAssignmentStatement::class.java)) {
                ProgressManager.checkCanceled()
                for (target in stmt.varList.varList) {
                    if (dottedMemberName(target) != qualifiedName) continue
                    memberFieldIdentifier(target)?.let { results.add(it) }
                }
            }
        }
        return results
    }
}
