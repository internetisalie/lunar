package net.internetisalie.lunar.lang.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ProcessingContext
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.*
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.syntax.extractLuaString
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

        requires.forEach { requireName ->
            resolveAndAddSymbols(
                project,
                requireName,
                sourcePathPatterns,
                prefix,
                result,
                visited,
                currentFile.virtualFile
            )
        }

        processGlobals(project, prefix, result)
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
        contextFile: VirtualFile? = null
    ) {
        if (visited.contains(requireName)) return
        visited.add(requireName)

        val virtualFile = findRequireFile(project, requireName, sourcePathPatterns, contextFile) ?: return
        
        val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile) as? LuaFile ?: return

        addSymbolsFromFile(psiFile, prefix, result)
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

    private fun addSymbolsFromFile(file: LuaFile, prefix: String, result: CompletionResultSet) {
        val processor = LuaCrossFileCompletionScopeProcessor(prefix)
        file.processDeclarations(processor, com.intellij.psi.ResolveState.initial(), file, file)
        
        processor.results.forEach { (symbolName, element) ->
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

    private fun processGlobals(
        project: Project,
        prefix: String,
        result: CompletionResultSet
    ) {
        val scope = GlobalSearchScope.allScope(project)

        // Process global function declarations
        val allKeys = StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, project)
        allKeys.filter { it.startsWith(prefix) }.forEach { key ->
            StubIndex.getElements(
                LuaGlobalDeclarationIndex.KEY,
                key,
                project,
                scope,
                LuaFuncDecl::class.java
            ).forEach { funcDecl ->
                val name = (funcDecl as? PsiNamedElement)?.name ?: return@forEach
                if (name.startsWith(prefix)) {
                    val sourceFile = funcDecl.containingFile?.name ?: "unknown"
                    val builder = LookupElementBuilder.create(name)
                        .withTypeText(sourceFile)
                        .withIcon(com.intellij.icons.AllIcons.Nodes.Function)

                    result.addElement(builder)
                }
            }
        }

        // Process class declarations - iterate over keys to get class names
        val classKeys = StubIndex.getInstance().getAllKeys(LuaClassNameIndex.KEY, project)
        classKeys.filter { it.startsWith(prefix) }.forEach { className ->
            val builder = LookupElementBuilder.create(className)
                .withTypeText("class")
                .withIcon(com.intellij.icons.AllIcons.Nodes.Class)
            result.addElement(builder)
        }
    }
}
