package net.internetisalie.lunar.lang

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.elementType
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicInteger
import net.internetisalie.lunar.lang.LuaIcons.FILE
import net.internetisalie.lunar.lang.indexing.*
import net.internetisalie.lunar.lang.navigation.LuaMemberFieldNavigation
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaIndexExpr
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaResolveUtil
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.LuaVarSuffix
import net.internetisalie.lunar.project.PlatformLibraryIndex

class LuaNameReference(element: PsiElement, textRange: TextRange) :
    PsiReferenceBase<PsiElement?>(element, textRange), PsiPolyVariantReference {
    private val name = element.text.substring(textRange.startOffset, textRange.endOffset)

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val hostElement = myElement ?: return ResolveResult.EMPTY_ARRAY
        return ResolveCache.getInstance(hostElement.project)
            .resolveWithCaching(this, RESOLVER, /* needToPreventRecursion = */ false, incompleteCode)
    }

    private fun doMultiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        RESOLVE_INVOCATIONS.incrementAndGet() // TC-03 observation seam: counts un-cached compute entries.
        val element = myElement ?: return emptyArray()
        val results = mutableListOf<ResolveResult>()

        // === PHASE 1: Local Resolution (LAZY) ===
        // Sole owner of the bottom-up scope walk is LuaResolveUtil.scopeCrawlUp (§2.1, §3.3). It
        // passes `prev` (the child ascended from) as `lastParent`, excluding the reference's own
        // declaring statement from scope — so the RHS of a self-referential `local x = x` resolves to
        // the outer/undeclared `x`, not the new local (Lua §3.3.3/§3.5). TC-02 locks this.
        val processor = LuaScopeProcessor(name)
        LuaResolveUtil.scopeCrawlUp(processor, element)

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
            (element.containingFile as? LuaFile)?.let { fileRequires(it) } ?: emptyList(),
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

    companion object {
        // Static singleton so the ResolveCache key identity is stable across calls (a per-call
        // lambda would defeat the cache). needToPreventRecursion=false: Lua name resolution has no
        // reference->reference recursion (Phase-2 uses the stub index, not .resolve()).
        private val RESOLVER =
            ResolveCache.PolyVariantResolver<LuaNameReference> { ref, incomplete -> ref.doMultiResolve(incomplete) }

        /** TC-03 observation seam: counts entries into [doMultiResolve] (the un-cached compute path). */
        @VisibleForTesting
        internal val RESOLVE_INVOCATIONS = AtomicInteger()
    }
}
