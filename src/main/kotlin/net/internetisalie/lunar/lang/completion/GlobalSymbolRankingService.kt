package net.internetisalie.lunar.lang.completion

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.io.File

/**
 * Provides project-wide global symbol suggestions with proximity-based ranking.
 * Implements COMP-03-02 Phase 2: Global Symbol Suggestions.
 *
 * Responsibilities:
 * - Query LuaGlobalDeclarationIndex and LuaClassNameIndex
 * - Filter by visibility (suppress _-prefixed symbols)
 * - Deduplicate with local and imported symbols
 * - Rank by proximity (same module, same directory, different module)
 */
@Service(Service.Level.PROJECT)
class GlobalSymbolRankingService(private val project: Project) {

    data class GlobalSymbolCompletion(
        val name: String,
        val psiElement: PsiElement,
        val proximityWeight: Double,
        val isClassType: Boolean = false,
        val sourceVirtualFile: VirtualFile? = null,
    )

    /**
     * Get project-wide global symbols with proximity ranking.
     *
     * @param currentFile The file where completion is triggered
     * @param localSymbolNames Set of symbol names already in local scope (to deduplicate)
     * @param importedSymbolNames Set of symbol names from required modules (to deduplicate)
     * @return List of global symbol completions sorted by proximity weight (descending)
     */
    fun getProjectGlobalSymbols(
        currentFile: LuaFile,
        localSymbolNames: Set<String>,
        importedSymbolNames: Set<String>
    ): List<GlobalSymbolCompletion> {
        // Graceful degradation in dumb mode
        if (DumbService.isDumb(project)) {
            return emptyList()
        }

        val allSymbols = mutableListOf<GlobalSymbolCompletion>()

        allSymbols.addAll(collectGlobalFunctions(currentFile, localSymbolNames, importedSymbolNames))
        allSymbols.addAll(collectClassSymbols(currentFile, localSymbolNames, importedSymbolNames))

        // Use improved deduplication by PSI element identity (Phase 2.2 enhancement)
        return DeduplicationService.deduplicateByPsiIdentity(allSymbols)
    }

    /**
     * Collect and filter global function declarations from the project.
     */
    private fun collectGlobalFunctions(
        currentFile: LuaFile,
        localSymbolNames: Set<String>,
        importedSymbolNames: Set<String>
    ): List<GlobalSymbolCompletion> {
        val result = mutableListOf<GlobalSymbolCompletion>()
        val settings = LuaProjectSettings.getInstance(project)
        val suppressUnderscore = settings.suppressUnderscorePrefixedGlobals
        val scope = GlobalSearchScope.projectScope(project)

        val allFuncKeys = StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, project)
         
        // Use labeled for loop to properly exit nested loops when limit is reached
        outer@ for (key in allFuncKeys) {
            // Check early exit and cancellation before processing each key
            if (result.size >= MAX_CANDIDATES) break
            ProgressManager.checkCanceled()
             
            val functions = StubIndex.getElements(
                LuaGlobalDeclarationIndex.KEY,
                key,
                project,
                scope,
                LuaFuncDecl::class.java
            )
             
            for (funcDecl in functions) {
                // Check cancellation in inner loop
                ProgressManager.checkCanceled()

                // Exit both loops if limit reached
                if (result.size >= MAX_CANDIDATES) break@outer

                val name = extractGlobalFuncName(funcDecl) ?: continue

                // Apply visibility rules
                if (suppressUnderscore && name.startsWith("_")) {
                    continue
                }

                // Deduplicate with local and imported symbols
                if (localSymbolNames.contains(name) || importedSymbolNames.contains(name)) {
                    continue
                }

                // Calculate proximity and recency weight
                val weight = ProximityCalculator.calculateWeight(
                    currentFile,
                    funcDecl.containingFile ?: continue,
                    isClassType = false
                )

                result.add(
                    GlobalSymbolCompletion(
                        name = name,
                        psiElement = funcDecl,
                        proximityWeight = weight,
                        isClassType = false,
                        sourceVirtualFile = funcDecl.containingFile?.virtualFile,
                    )
                )
            }
        }

        return result
    }

    /**
     * Collect and filter class declarations from the project.
     */
    private fun collectClassSymbols(
        currentFile: LuaFile,
        localSymbolNames: Set<String>,
        importedSymbolNames: Set<String>
    ): List<GlobalSymbolCompletion> {
        val result = mutableListOf<GlobalSymbolCompletion>()
        val settings = LuaProjectSettings.getInstance(project)
        val suppressUnderscore = settings.suppressUnderscorePrefixedGlobals
        val scope = GlobalSearchScope.projectScope(project)

        val allClassKeys = StubIndex.getInstance().getAllKeys(LuaClassNameIndex.KEY, project)
         
        // Use labeled for loop to properly exit nested loops when limit is reached
        outer@ for (className in allClassKeys) {
            // Check early exit and cancellation before processing each key
            if (result.size >= MAX_CANDIDATES) break
            ProgressManager.checkCanceled()
             
            val classes = StubIndex.getElements(
                LuaClassNameIndex.KEY,
                className,
                project,
                scope,
                LuaLocalVarDecl::class.java
            )
             
            for (classElement in classes) {
                // Check cancellation in inner loop
                ProgressManager.checkCanceled()

                // Exit both loops if limit reached
                if (result.size >= MAX_CANDIDATES) break@outer

                val name = extractClassElementName(classElement) ?: continue

                // Apply visibility rules
                if (suppressUnderscore && name.startsWith("_")) {
                    continue
                }

                // Deduplicate with local and imported symbols
                if (localSymbolNames.contains(name) || importedSymbolNames.contains(name)) {
                    continue
                }

                // Calculate proximity and recency weight (with class boost)
                val weight = ProximityCalculator.calculateWeight(
                    currentFile,
                    classElement.containingFile ?: continue,
                    isClassType = true
                )

                result.add(
                    GlobalSymbolCompletion(
                        name = name,
                        psiElement = classElement,
                        proximityWeight = weight,
                        isClassType = true,
                        sourceVirtualFile = classElement.containingFile?.virtualFile,
                    )
                )
            }
        }

        return result
    }

    /**
     * Extract the standalone-global name from a [LuaFuncDecl], or null to skip it (#40).
     *
     * A `function Class:method()` decl is stub-keyed as `"Class:method"` and a `function M.fn()` /
     * `function a.b.c()` decl as `"M.fn"` / `"a.b.c"`; their base identifier is a *receiver*
     * (`Class` / `M` / `a`), not a standalone global — offering it leaks the receiver into global
     * completion. Only a bare `function name()` (no `:method` separator, empty `.property` path)
     * is a real project global.
     */
    private fun extractGlobalFuncName(funcDecl: LuaFuncDecl): String? {
        val funcName = funcDecl.funcName ?: return null
        if (funcName.funcNameMethod != null) return null
        if (funcName.funcNamePropertyList.isNotEmpty()) return null
        return funcName.nameRef.identifier?.text
    }

    /**
    * Extract the name from a LuaLocalVarDecl (used for @class declarations).
    * Typically gets the first name from the local variable declaration.
    */
    private fun extractClassElementName(element: LuaLocalVarDecl): String? {
       val attNames = element.attNameList.firstOrNull() ?: return null
       val identifier = attNames.nameRef.identifier ?: return null
       return identifier.text
    }

    companion object {
       private const val MAX_CANDIDATES = 500

       fun getInstance(project: Project): GlobalSymbolRankingService =
           project.getService(GlobalSymbolRankingService::class.java)
    }
}
