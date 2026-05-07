package net.internetisalie.lunar.lang.doc

import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.indexing.LuaAliasIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes

class LuaDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        var element = file.findElementAt(offset) ?: return emptyList()
        val et = element.elementType
        if (et == LuaElementTypes.IDENTIFIER || et == LuaCatsElementTypes.NAME || et == LuaCatsElementTypes.BUILTIN_TYPE) {
            element = resolveDocumentationTarget(element) ?: return emptyList()
        }
        if (element is LuaCatsCommentOwner) {
            return arrayListOf(LuaCatsDocumentationTarget(element))
        }
        return emptyList()
    }

    private fun resolveDocumentationTarget(element: PsiElement): PsiElement? {
        // First try resolving through reference (for call sites)
        val parent = element.parent

        val resolvedElement = when {
            parent is LuaNameRefElement -> {
                val ref = parent.reference
                var resolved = ref?.resolve()

                // The reference might resolve to a name token or another leaf element
                // Get the parent to get the actual declaration
                if (resolved != null && resolved !is LuaCatsCommentOwner) {
                    // First try to unwrap it to a declaration
                    val commentOwner = findElementDocCommentOwner(resolved)
                    if (commentOwner != null) {
                        resolved = commentOwner
                    } else {
                        // If that fails, try the resolved element's parent directly
                        // (in case it's already wrapped in a declaration)
                        val p = resolved.parent
                        if (p is LuaCatsCommentOwner) {
                            resolved = p
                        }
                    }
                }
                resolved
            }
            parent is PsiReference -> parent.resolve()
            element is PsiReference -> element.resolve()
            else -> null
        }

        if (resolvedElement != null && resolvedElement is LuaCatsCommentOwner) {
            return resolvedElement
        }

        // Resolve upwards (for identifiers that are part of declarations)
        val ownerElement = findElementDocCommentOwner(element)
        if (ownerElement != null) {
            return ownerElement
        }

        // Try cross-file type lookup using PSI search and stubs
        val elementText = element.text ?: return null
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)

        val classDecl = StubIndex.getElements(LuaClassNameIndex.KEY, elementText, project, scope, LuaLocalVarDecl::class.java).firstOrNull()
        if (classDecl != null) return classDecl

        val aliasDecl = StubIndex.getElements(LuaAliasIndex.KEY, elementText, project, scope, LuaLocalVarDecl::class.java).firstOrNull()
        if (aliasDecl != null) return aliasDecl

        val funcDecl = StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, elementText, project, scope, LuaFuncDecl::class.java).firstOrNull()
        if (funcDecl != null) return funcDecl

        // Fallback for member functions like math.abs
        if (parent is LuaNameRefElement) {
            val topExpr = PsiTreeUtil.getTopmostParentOfType(parent, LuaExpr::class.java)
            if (topExpr != null) {
                val fullName = topExpr.text
                if (fullName != null && fullName != elementText) {
                    return StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, fullName, project, scope, LuaFuncDecl::class.java).firstOrNull()
                }
            }
        }

        return null
    }

    private fun findElementDocCommentOwner(element: PsiElement): LuaCatsCommentOwner? {
        val owner = PsiTreeUtil.getParentOfType(element, LuaCatsCommentOwner::class.java) ?: return null

        return when (owner) {
            is LuaFuncDecl -> owner
            is LuaLocalFuncDecl -> owner
            is LuaLocalVarDecl -> {
                val catsComment = owner.catsComment
                if (catsComment != null && (
                    catsComment.classTagList.isNotEmpty() ||
                    catsComment.typeTagList.isNotEmpty() ||
                    catsComment.enumTagList.isNotEmpty())
                ) {
                    owner
                } else {
                    null
                }
            }
            else -> null
        }
    }
}


internal class LuaCatsDocumentationTarget(
    val element: LuaCatsCommentOwner,
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val elementPtr = element.createSmartPointer()

        return Pointer {
            val element = elementPtr.dereference() ?: return@Pointer null
            LuaCatsDocumentationTarget(element)
        }
    }

    override fun computePresentation(): TargetPresentation {
        return targetPresentation(element)
    }

    override val navigatable: Navigatable?
        get() = element as? Navigatable

    override fun computeDocumentation(): DocumentationResult? {
        return DocumentationResult.documentation(
            LuaDocumentationRenderer.renderFullDocumentation(element) ?: return null
        )
    }
}

