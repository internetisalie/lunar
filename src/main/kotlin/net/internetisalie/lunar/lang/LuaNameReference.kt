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
import net.internetisalie.lunar.lang.insight.LuaBindingsVisitor
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.project.PlatformLibraryIndex

class LuaNameReference(element: PsiElement, textRange: TextRange) :
    PsiReferenceBase<PsiElement?>(element, textRange), PsiPolyVariantReference {
    private val name = element.text.substring(textRange.startOffset, textRange.endOffset)

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val element = myElement ?: return emptyArray()
        val bindings = LuaBindingsVisitor.getBindings(element)
        val reference = bindings.references[element.textOffset] ?: return emptyArray()
        val results = mutableListOf<ResolveResult>()

        // Use internal definition
        if (reference.defined) {
            results.add(PsiElementResolveResult(reference.binding!!.element))
        }

        // Find external definitions
        if (reference.global) {
            val platformQuery = VirtualFilesQuery(
                ProjectScope.getLibrariesScope(element.project),
                PlatformLibraryIndex.getPackageFiles(element.project),
            )
            val requiresQuery = RequiredFilesQuery(
                ProjectScope.getProjectScope(element.project),
                PathConfiguration.getProjectSourcePathPatterns(element.project),
                bindings.requires,
            )

            val importedResults = queryFiles(platformQuery, requiresQuery)

            val referenceName = reference.name.joinToString(".")

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
        val element = myElement ?: return emptyArray()
        val bindings = LuaBindingsVisitor.getBindings(element)
        val reference = bindings.references[element.textOffset] ?: return emptyArray()
        if (!reference.defined) return emptyArray()
        return arrayOf(
            LookupElementBuilder
                .create(reference.binding!!.element)
                .withIcon(FILE)
                .withTypeText(element.containingFile.name)
        )
    }
}
