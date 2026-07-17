package net.internetisalie.lunar.lang.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ProcessingContext
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.*
import net.internetisalie.lunar.lang.path.LuaModulePathResolver
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.syntax.extractLuaString
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.io.File

class LuaCrossFileCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position
        val file = position.containingFile as? LuaFile ?: return
        val project = file.project

        val prefix = result.prefixMatcher.prefix

        processCrossFileSymbols(project, file, prefix, result)
    }

    private fun processCrossFileSymbols(
        project: Project,
        currentFile: LuaFile,
        prefix: String,
        result: CompletionResultSet
    ) {
        val sourcePathPatterns = PathConfiguration.getProjectSourcePathPatterns(project)
        val requires = extractRequires(currentFile)
        val visited = mutableSetOf<String>()

        // Collect imported symbol names for deduplication
        val importedSymbolNames = mutableSetOf<String>()
        requires.forEach { requireName ->
            resolveAndAddSymbols(
                project,
                requireName,
                sourcePathPatterns,
                prefix,
                result,
                visited,
                currentFile.virtualFile,
                importedSymbolNames
            )
        }

        // Get local symbol names for deduplication
        val localSymbolNames = getLocalSymbolNames(currentFile)

        // Phase 2: Add project-wide globals with proximity weighting
        processGlobalsWithRanking(project, currentFile, prefix, result, localSymbolNames, importedSymbolNames)
    }

    private fun getLocalSymbolNames(currentFile: LuaFile): Set<String> {
        val processor = object : PsiScopeProcessor {
            val names = mutableSetOf<String>()

            override fun execute(element: PsiElement, state: ResolveState): Boolean {
                if (element is PsiNamedElement) {
                    element.name?.let { names.add(it) }
                }
                return true
            }

            override fun <T : Any?> getHint(hintKey: Key<T>): T? = null
            override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {}
        }

        currentFile.processDeclarations(processor, ResolveState.initial(), currentFile, currentFile)
        return processor.names
    }

    private fun extractRequires(file: LuaFile): List<String> {
        val cachedValue: CachedValue<List<String>> = CachedValuesManager.getManager(file.project)
            .createCachedValue(
                {
                    val index = FileBasedIndex.getInstance()
                    val scope = GlobalSearchScope.fileScope(file)
                    val record = index.getValues(LuaFileBindingsIndexName, ForwardIndexer.KEY, scope)
                        .firstOrNull()
                    
                    CachedValueProvider.Result.create(
                        record?.requires ?: emptyList(),
                        PsiModificationTracker.MODIFICATION_COUNT
                    )
                },
                /* trackValue = */ false
            )
        
        return cachedValue.value
    }

    private fun resolveAndAddSymbols(
        project: Project,
        requireName: String,
        sourcePathPatterns: List<net.internetisalie.lunar.lang.path.SourcePathPattern>,
        prefix: String,
        result: CompletionResultSet,
        visited: MutableSet<String>,
        contextFile: VirtualFile? = null,
        importedSymbolNames: MutableSet<String>? = null
    ) {
        if (visited.contains(requireName)) return
        visited.add(requireName)

        val virtualFile = findRequireFile(project, requireName, sourcePathPatterns, contextFile) ?: return

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? LuaFile ?: return

        addSymbolsFromFile(psiFile, prefix, result, importedSymbolNames)

        // Recursively add symbols from dependencies
        val childRequires = extractRequires(psiFile)
        childRequires.forEach { childRequireName ->
            resolveAndAddSymbols(
                project,
                childRequireName,
                sourcePathPatterns,
                prefix,
                result,
                visited,
                virtualFile,
                importedSymbolNames
            )
        }
    }

    private fun findRequireFile(
        project: Project,
        requireName: String,
        sourcePathPatterns: List<net.internetisalie.lunar.lang.path.SourcePathPattern>,
        contextFile: VirtualFile? = null
    ): VirtualFile? {
        // Handle relative requires (require("./mod") or require("../mod"))
        if (requireName.startsWith("./") || requireName.startsWith("../")) {
            val contextDir = contextFile?.parent
            if (contextDir != null) {
                // Try with .lua extension and /init.lua suffix
                val extensions = listOf("", ".lua", "/init.lua")
                for (ext in extensions) {
                    val resolvedPath = contextDir.path + "/" + requireName + ext
                    val normalizedPath = normalizePath(resolvedPath)
                    
                    LocalFileSystem.getInstance().findFileByPath(normalizedPath)?.let { return it }
                    VfsUtil.findFileByIoFile(File(normalizedPath), false)?.let { return it }
                }
            }
            return null
        }
        
        // Handle absolute module names via source path patterns
        sourcePathPatterns.forEach { pattern ->
            val path = pattern.interpolate(requireName)
            
            // Try LocalFileSystem first (more reliable for local paths)
            LocalFileSystem.getInstance().findFileByPath(path)?.let { return it }
            
            // Fallback: try VfsUtil without refresh
            VfsUtil.findFileByIoFile(File(path), false)?.let { return it }
        }
        return null
    }
    
    private fun normalizePath(path: String): String {
        // Normalize path by resolving . and .. components
        // Use File to handle platform-specific separators
        val file = File(path)
        val normalized = file.normalize().absolutePath
        
        // Convert to system-independent format (forward slashes)
        return normalized.replace(File.separatorChar, '/')
    }

    private fun addSymbolsFromFile(file: LuaFile, prefix: String, result: CompletionResultSet, importedSymbolNames: MutableSet<String>? = null) {
        val processor = LuaCrossFileCompletionScopeProcessor(prefix)
        file.processDeclarations(processor, com.intellij.psi.ResolveState.initial(), file, file)
        
        processor.results.forEach { (symbolName, element) ->
            importedSymbolNames?.add(symbolName)
            val icon = when (element) {
                is LuaFuncDecl -> com.intellij.icons.AllIcons.Nodes.Function
                is LuaLocalVarDecl -> com.intellij.icons.AllIcons.Nodes.Variable
                is LuaLocalFuncDecl -> com.intellij.icons.AllIcons.Nodes.Function
                else -> com.intellij.icons.AllIcons.Nodes.Variable
            }
            val builder = LookupElementBuilder.create(symbolName)
                .withTypeText(file.name)
                .withIcon(icon)
            result.addElement(builder)
        }
    }

    private fun processGlobalsWithRanking(
        project: Project,
        currentFile: LuaFile,
        prefix: String,
        result: CompletionResultSet,
        localSymbolNames: Set<String>,
        importedSymbolNames: Set<String>
    ) {
        val rankingService = GlobalSymbolRankingService.getInstance(project)
        val globalSymbols = rankingService.getProjectGlobalSymbols(currentFile, localSymbolNames, importedSymbolNames)

        val showHints = LuaProjectSettings.getInstance(project).showAutoImportHints

        globalSymbols.filter { it.name.startsWith(prefix) }.forEach { symbol ->
            val builder = buildGlobalLookupElement(symbol, showHints)

            // Apply proximity-based weighting
            val weighted = com.intellij.codeInsight.completion.PrioritizedLookupElement.withPriority(
                builder,
                symbol.proximityWeight
            )
            result.addElement(weighted)
        }
    }

    /**
     * Build a lookup element for a project-wide global. Non-imported symbols (Phase 3)
     * receive a [LuaAutoImportInsertHandler] and an optional "(auto-import)" tail text.
     */
    private fun buildGlobalLookupElement(
        symbol: GlobalSymbolRankingService.GlobalSymbolCompletion,
        showHints: Boolean
    ): LookupElementBuilder {
        val icon = if (symbol.isClassType) {
            com.intellij.icons.AllIcons.Nodes.Class
        } else {
            com.intellij.icons.AllIcons.Nodes.Function
        }
        val sourceFile = symbol.psiElement.containingFile?.name ?: "unknown"

        var builder = LookupElementBuilder.create(symbol.name)
            .withTypeText(sourceFile)
            .withIcon(icon)

        val targetFile = symbol.sourceVirtualFile
        if (targetFile != null) {
            builder = builder.withInsertHandler(
                LuaAutoImportInsertHandler(
                    targetFile = targetFile,
                    modulePathResolver = LuaModulePathResolver(),
                    exportStyleDetector = LuaExportStyleDetector(),
                    importNameResolver = LuaImportNameResolver(),
                )
            )
            if (showHints) {
                builder = builder.withTailText(" (auto-import)", true)
            }
        }
        return builder
    }
}
