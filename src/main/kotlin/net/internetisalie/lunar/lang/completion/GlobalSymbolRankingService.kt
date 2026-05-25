package net.internetisalie.lunar.lang.completion

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
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
        val isClassType: Boolean = false
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

        return deduplicateAndSort(allSymbols)
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
        allFuncKeys.forEach { key ->
            ProgressManager.checkCanceled()
            StubIndex.getElements(
                LuaGlobalDeclarationIndex.KEY,
                key,
                project,
                scope,
                LuaFuncDecl::class.java
            ).forEach { funcDecl ->
                val name = extractFuncDeclName(funcDecl) ?: return@forEach

                // Apply visibility rules
                if (suppressUnderscore && name.startsWith("_")) {
                    return@forEach
                }

                // Deduplicate with local and imported symbols
                if (localSymbolNames.contains(name) || importedSymbolNames.contains(name)) {
                    return@forEach
                }

                // Calculate proximity weight
                val proximityWeight = calculateProximityWeight(currentFile, funcDecl.containingFile ?: return@forEach)

                result.add(
                    GlobalSymbolCompletion(
                        name = name,
                        psiElement = funcDecl,
                        proximityWeight = proximityWeight,
                        isClassType = false
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
        allClassKeys.forEach { className ->
            ProgressManager.checkCanceled()
            StubIndex.getElements(
                LuaClassNameIndex.KEY,
                className,
                project,
                scope,
                LuaLocalVarDecl::class.java
            ).forEach { classElement ->
                val name = extractClassElementName(classElement) ?: return@forEach

                // Apply visibility rules
                if (suppressUnderscore && name.startsWith("_")) {
                    return@forEach
                }

                // Deduplicate with local and imported symbols
                if (localSymbolNames.contains(name) || importedSymbolNames.contains(name)) {
                    return@forEach
                }

                // Calculate proximity weight (using currentFile correctly)
                val proximityWeight = calculateProximityWeight(currentFile, classElement.containingFile ?: return@forEach)
                val boostedWeight = proximityWeight + 0.25

                result.add(
                    GlobalSymbolCompletion(
                        name = name,
                        psiElement = classElement,
                        proximityWeight = boostedWeight,
                        isClassType = true
                    )
                )
            }
        }

        return result
    }

    /**
     * Deduplicate symbols by name (keeping highest weight) and sort by proximity (descending).
     */
    private fun deduplicateAndSort(symbols: List<GlobalSymbolCompletion>): List<GlobalSymbolCompletion> {
        val deduped = mutableMapOf<String, GlobalSymbolCompletion>()
        symbols.forEach { symbol ->
            val existing = deduped[symbol.name]
            if (existing == null || symbol.proximityWeight > existing.proximityWeight) {
                deduped[symbol.name] = symbol
            }
        }

        return deduped.values.sortedByDescending { it.proximityWeight }
    }

    /**
     * Extract the simple name from a LuaFuncDecl.
     * Handles nested function names (e.g., "a.b.c" for function a.b.c() end).
     * For completion purposes, returns the first identifier only.
     */
    private fun extractFuncDeclName(funcDecl: LuaFuncDecl): String? {
        val funcName = funcDecl.funcName ?: return null
        val identifier = funcName.nameRef.identifier ?: return null
        return identifier.text
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

    /**
     * Calculate proximity weight based on file/module structure.
     *
     * Weights:
     * - Same module: 0.9
     * - Same directory: 0.7
     * - Different module: 0.5
     */
    private fun calculateProximityWeight(currentFile: PsiFile, symbolFile: PsiFile): Double {
        if (currentFile == symbolFile) {
            return 0.9 // Same file
        }

        val currentPath = currentFile.virtualFile?.path ?: return 0.5
        val symbolPath = symbolFile.virtualFile?.path ?: return 0.5

        // Same directory
        val currentDir = File(currentPath).parent
        val symbolDir = File(symbolPath).parent
        if (currentDir != null && currentDir == symbolDir) {
            return 0.7
        }

        // Different module/directory
        return 0.5
    }

    companion object {
        fun getInstance(project: Project): GlobalSymbolRankingService =
            project.getService(GlobalSymbolRankingService::class.java)
    }
}

