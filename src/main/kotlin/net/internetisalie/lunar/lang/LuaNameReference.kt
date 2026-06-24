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
import net.internetisalie.lunar.lang.navigation.LuaMemberFieldNavigation
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaIndexExpr
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.syntax.extractLuaString
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.LuaVarSuffix
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
        var prev: PsiElement? = null
        var current: PsiElement? = element

        while (current != null && current !is PsiFile) {
            val state = ResolveState.initial()

            // Process declarations in scope elements
            val matchFound = when (current) {
                is LuaBlock -> !current.processDeclarations(processor, state, element, element)
                is LuaFuncDef -> !current.processDeclarations(processor, state, element, element)
                is LuaFuncDecl -> !current.processDeclarations(processor, state, element, element)
                is LuaLocalFuncDecl -> !current.processDeclarations(processor, state, element, element)
                // Pass `prev` (the child we ascended from) as lastParent: a for-loop variable is
                // visible only when the reference sits in the loop body block (prev == block), not
                // in the iterator expression. processDeclarations gates loop-var visibility on
                // `lastParent == block`, which the deep reference element never satisfies.
                is LuaNumericForStatement -> !current.processDeclarations(processor, state, prev ?: element, element)
                is LuaGenericForStatement -> !current.processDeclarations(processor, state, prev ?: element, element)
                else -> false
            }

            if (matchFound) break

            prev = current
            current = current.parent
        }

        // Also process the file itself
        if (current is LuaFile && processor.result == null) {
            current.processDeclarations(processor, ResolveState.initial(), element, element)
        }

        if (processor.result != null) {
            results.add(PsiElementResolveResult(processor.result!!))
            return results.toTypedArray()
        }

        // === PHASE 2: External Resolution (unchanged) ===
        // Only proceed if local resolution failed
        val platformQuery = VirtualFilesQuery(
            GlobalSearchScope.allScope(element.project),
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

        val qualifiedName = getQualifiedName(element)

        if (qualifiedName != null) {
            // A dotted member access `a.b`: resolve `b` only through the receiver-qualified name.
            // The bare-name lookups below treat the short name as a *receiver* (the declaration index
            // is keyed by receiver), so a bare "path" lookup for `package.path` would pull in every
            // `path.*` function of an unrelated module. Restrict member segments to the qualified name.
            StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, qualifiedName, project, scope, LuaFuncDecl::class.java).forEach { decl ->
                results.add(PsiElementResolveResult(decl))
            }
            // NAV-12: a dotted field assignment `receiver.field = value` (not stubbed) is reachable by
            // its qualified name through the member-field index — keyed by the full name, so it never
            // collides with an unrelated `path.*` module.
            LuaMemberFieldNavigation.find(project, qualifiedName, scope).forEach { field ->
                if (field != element) results.add(PsiElementResolveResult(field))
            }
            return results.distinctBy { it.element }.toTypedArray()
        }

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

    private fun getQualifiedName(element: PsiElement): String? {
        if (element !is LuaNameRef) return null
        val parent = element.parent
        if (parent is LuaIndexExpr) {
            val isDot = parent.node.findChildByType(LuaElementTypes.DOT) != null
            if (!isDot) return null

            val varSuffix = parent.parent as? LuaVarSuffix ?: return null
            val luaVar = varSuffix.parent as? LuaVar ?: return null

            val sb = StringBuilder()
            val baseNameRef = luaVar.nameRef
            if (baseNameRef == null) {
                // System.out.println("  No base name ref in var")
                return null
            }
            sb.append(baseNameRef.text)

            for (suffix in luaVar.varSuffixList) {
                val indexExpr = suffix.indexExpr
                if (indexExpr.node.findChildByType(LuaElementTypes.DOT) != null) {
                    val mNameRef = indexExpr.nameRef
                    if (mNameRef != null) {
                        sb.append(".")
                        sb.append(mNameRef.text)
                        if (mNameRef == element) {
                            val result = sb.toString()
                            // System.out.println("  Reconstructed qualified name: $result")
                            return result
                        }
                    } else break
                } else break
            }
        }
        return null
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
        // A declaration's own name is not a usage of itself — exclude it from Find Usages
        // (otherwise the declaration site, which resolves to itself, is counted as a usage).
        val self = myElement
        if (self is LuaNameRef && self.identifier === element) return false
        if (element.elementType != LuaElementTypes.IDENTIFIER || element.text != name) return false
        val resolved = resolve() ?: return false
        // Phase-1 (local) resolution returns the IDENTIFIER leaf; Phase-2 (stub-index, cross-file)
        // returns the declaration element — normalize the latter to its name identifier so a
        // cross-file usage still matches the declaration's leaf target.
        return resolved === element || declarationIdentifier(resolved) === element
    }

    private fun declarationIdentifier(decl: PsiElement): PsiElement? = when (decl) {
        is LuaFuncDecl -> decl.funcName.funcNameMethod?.nameRef?.identifier ?: decl.funcName.nameRef.identifier
        is LuaLocalVarDecl -> decl.attNameList.firstOrNull()?.nameRef?.identifier
        else -> null
    }

    override fun getVariants(): Array<Any> {
        // Code completion: return empty for now
        // (Modern IntelliJ uses CompletionContributor instead of PsiReference.getVariants())
        return emptyArray()
    }
}
