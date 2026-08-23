package net.internetisalie.lunar.lang.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaGlobalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGlobalVarDecl

/**
 * Resolves a bare global name to the declaration(s) that bind it, anywhere in the project
 * (BUG-391). Backed by [LuaGlobalAssignmentIndex]; re-resolves the target identifier in each
 * containing file, mirroring [LuaMemberFieldNavigation] for the dotted case.
 *
 * Three of the index's four declaration forms are re-collected here (REFACT-01, design §2.10): a
 * bare assignment, and the Lua 5.5 `global x = 1` / `global function f() end` declarations. A form
 * the index records but nothing re-collects resolves to nothing, which is the shape of defect that
 * makes a cross-file rename half-apply.
 *
 * The fourth — a bare `function f() end` — is deliberately absent: `LuaFuncDecl` is stub-indexed,
 * so [net.internetisalie.lunar.lang.LuaNameReference] already resolves it through
 * `LuaGlobalDeclarationIndex`. The Lua 5.5 forms have no stub of their own, which is why they need
 * a collector here.
 */
object LuaGlobalAssignmentNavigation {
    fun find(
        project: Project,
        name: String,
        scope: GlobalSearchScope,
    ): List<PsiElement> {
        val index = FileBasedIndex.getInstance()
        val psiManager = PsiManager.getInstance(project)
        val results = mutableListOf<PsiElement>()
        for (virtualFile in index.getContainingFiles(LuaGlobalAssignmentIndex.KEY, name, scope)) {
            val luaFile = psiManager.findFile(virtualFile) as? LuaFile ?: continue
            // File-scope statements only, matching what the indexer recorded.
            luaFile.getBlockList().flatMap { it.statementList }.forEach { statement ->
                when (statement) {
                    is LuaAssignmentStatement -> collectTargets(statement, name, results)
                    is LuaGlobalVarDecl -> collectGlobalVarNames(statement, name, results)
                    is LuaGlobalFuncDecl ->
                        statement.nameRef
                            ?.takeIf { it.text == name }
                            ?.let { results.add(it.identifier) }
                    else -> Unit
                }
            }
        }
        return results
    }

    /**
     * Lua 5.5 `global a, b = 1, 2` — an `attName` hangs directly off the declaration exactly as it
     * does off a `local`, so the declared leaves are the `attName`s' name refs.
     */
    private fun collectGlobalVarNames(
        decl: LuaGlobalVarDecl,
        name: String,
        into: MutableList<PsiElement>,
    ) {
        decl.attNameList.forEach { attName ->
            if (attName.nameRef.text == name) into.add(attName.nameRef.identifier)
        }
    }

    private fun collectTargets(
        stmt: LuaAssignmentStatement,
        name: String,
        into: MutableList<PsiElement>,
    ) {
        stmt.varList.varList.forEach { target ->
            if (target.varSuffixList.isEmpty() && target.nameRef?.text == name) {
                target.nameRef?.identifier?.let { into.add(it) }
            }
        }
    }
}
