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
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner

class LuaDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        var element = file.findElementAt(offset) ?: return emptyList()
        if (element.elementType == LuaElementTypes.IDENTIFIER) {
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
        
        val resolvedElement = parent?.let {
            when {
                it is LuaNameRefElement -> {
                    val ref = it.reference
                    var resolved = ref?.resolve()
                    
                    // The reference resolves to the name token, not the declaration
                    // Get the parent to get the actual declaration
                    if (resolved != null && resolved !is LuaCatsCommentOwner) {
                        val commentOwner = findElementDocCommentOwner(resolved)
                        if (commentOwner != null) {
                            resolved = commentOwner
                        }
                    }
                    resolved
                }
                it is PsiReference -> it.resolve()
                else -> null
            }
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
        val scope = GlobalSearchScope.projectScope(element.project)
        val classDecl = StubIndex.getElements(LuaClassNameIndex.KEY, elementText, element.project, scope, LuaLocalVarDecl::class.java).firstOrNull()
        return classDecl
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

