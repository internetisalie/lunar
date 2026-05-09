package net.internetisalie.lunar.lang

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.LuaIcons.FILE
import net.internetisalie.lunar.lang.indexing.*
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.syntax.extractLuaString
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.project.PlatformLibraryIndex

class LuaNameReference(element: PsiElement, textRange: TextRange) :
    PsiReferenceBase<PsiElement?>(element, textRange), PsiPolyVariantReference {
    private val name = element.text.substring(textRange.startOffset, textRange.endOffset)

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val element = myElement ?: return emptyArray()
        val results = mutableListOf<ResolveResult>()

        // === PHASE 1: Local Resolution (LAZY) ===
        // Walk up the PSI tree and process declarations at each scope
        val processor = LuaScopeProcessor(name)
        var current: PsiElement? = element
        
        while (current != null && current !is PsiFile) {
            // Process declarations in scope elements
            when (current) {
                is LuaBlock -> {
                    if (!current.processDeclarations(processor, ResolveState.initial(), element, element)) break
                }
                is LuaFuncDef -> {
                    if (!current.processDeclarations(processor, ResolveState.initial(), element, element)) break
                }
                is LuaFuncDecl -> {
                    if (!current.processDeclarations(processor, ResolveState.initial(), element, element)) break
                }
                is LuaLocalFuncDecl -> {
                    if (!current.processDeclarations(processor, ResolveState.initial(), element, element)) break
                }
                is LuaNumericForStatement -> {
                    if (!current.processDeclarations(processor, ResolveState.initial(), element, element)) break
                }
                is LuaGenericForStatement -> {
                    if (!current.processDeclarations(processor, ResolveState.initial(), element, element)) break
                }
            }
            current = current.parent
        }
        
        // Also process the file itself
        if (current is LuaFile && !current.processDeclarations(processor, ResolveState.initial(), element, element)) {
            // Found a match in file
        }

        if (processor.result != null) {
            results.add(PsiElementResolveResult(processor.result!!))
            return results.toTypedArray()
        }

        // === PHASE 2: External Resolution (unchanged) ===
        // Only proceed if local resolution failed
        val platformQuery = VirtualFilesQuery(
            ProjectScope.getLibrariesScope(element.project),
            PlatformLibraryIndex.getPackageFiles(element.project),
        )
        val requiresQuery = RequiredFilesQuery(
            ProjectScope.getProjectScope(element.project),
            PathConfiguration.getProjectSourcePathPatterns(element.project),
            extractRequires(element.containingFile),
        )

        val importedResults = queryFiles(platformQuery, requiresQuery)

        val referenceName = name

        importedResults.forEach { filesQueryResults ->
            filesQueryResults.results.forEach { filesQueryResult ->
                collectFileResults(results, referenceName, filesQueryResult)
            }
        }

        val project = element.project
        val scope = GlobalSearchScope.allScope(project)

        StubIndex.getElements(LuaClassNameIndex.KEY, referenceName, project, scope, LuaLocalVarDecl::class.java).forEach { decl ->
            results.add(PsiElementResolveResult(decl))
        }

        StubIndex.getElements(LuaAliasIndex.KEY, referenceName, project, scope, LuaLocalVarDecl::class.java).forEach { decl ->
            results.add(PsiElementResolveResult(decl))
        }

        StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, referenceName, project, scope, LuaFuncDecl::class.java).forEach { decl ->
            results.add(PsiElementResolveResult(decl))
        }

        return results.distinctBy { it.element }.toTypedArray()
    }

    private fun collectFileResults(
        results: MutableList<ResolveResult>,
        referenceName: String,
        filesQueryResult: FilesQueryResult,
    ) {
        val psiFile = PsiManager.getInstance(element.project).findFile(filesQueryResult.packageFile.virtualFile) ?: return
        filesQueryResult.bindings.forEach { binding ->
            if (binding.name != referenceName) return@forEach
            val targetElement = psiFile.findElementAt(binding.textOffset) ?: return@forEach
            results.add(PsiElementResolveResult(targetElement))
        }
    }

    private fun extractRequires(file: PsiFile): List<String> {
        val requires = mutableListOf<String>()
        if (file !is LuaFile) return requires
        
        // Walk all statements in all blocks to find require() calls
        file.getBlockList().forEach { block ->
            block.statementList.forEach { stmt ->
                extractRequiresFromStatement(stmt, requires)
            }
        }
        return requires
    }

    private fun extractRequiresFromStatement(stmt: PsiElement?, requires: MutableList<String>) {
        if (stmt == null) return
        
        // Recursively walk to find require() calls
        stmt.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is LuaFuncCall) {
                    // Try to extract require() call
                    val varOrExp = element.varOrExp ?: return@visitElement
                    val luaVar = varOrExp.`var` ?: return@visitElement
                    
                    // Check if function name is "require"
                    val nameAndArgsList = element.nameAndArgsList
                    if (nameAndArgsList.isEmpty()) return@visitElement
                    
                    if (luaVar.nameRef?.identifier?.text != "require") return@visitElement
                    
                    // Extract string argument
                    val nameAndArgs = nameAndArgsList[0]
                    val args = nameAndArgs.args ?: return@visitElement
                    
                    // Try to get string from args
                    var stringElem = args.string
                    if (stringElem == null) {
                        // Try to get from exprList
                        val exprList = args.exprList?.exprList ?: return@visitElement
                        if (exprList.size == 1) {
                            val expr = exprList[0]
                            if (expr is LuaTerminalExpr) {
                                stringElem = expr.string
                            }
                        }
                    }
                    
                    stringElem?.let {
                        val str = extractLuaString(it.text)
                        if (str != null && !requires.contains(str)) {
                            requires.add(str)
                        }
                    }
                }
                super.visitElement(element)
            }
        })
    }

    override fun resolve(): PsiElement? {
        val resolveResults = multiResolve(false)
        return if (resolveResults.size == 1) resolveResults[0].element else null
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        return element.elementType == LuaElementTypes.IDENTIFIER &&
                element.text == name &&
                resolve() === element
    }

    override fun getVariants(): Array<Any> {
        // Code completion: return empty for now
        // (Modern IntelliJ uses CompletionContributor instead of PsiReference.getVariants())
        return emptyArray()
    }
}
