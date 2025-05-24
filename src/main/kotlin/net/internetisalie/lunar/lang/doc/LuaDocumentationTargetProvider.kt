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
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.insight.LuaBindingsVisitor
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
        // Resolve upwards
        val ownerElement = findElementDocCommentOwner(element)
        if (ownerElement != null) {
            return ownerElement
        }

        // Resolve locally
        val bindings = LuaBindingsVisitor.getBindings(element)
        var reference = bindings.lookup(element) ?: return null

        // Resolve globally
        if (reference.global) {
            val bindingsWithImports = LuaBindingsVisitor.getBindingsWithImports(element)
            reference = bindingsWithImports.lookup(element) ?: return null
        }

        // Resolve reference upwards
        return if (reference.binding != null) {
            findElementDocCommentOwner(reference.binding.element)
        } else {
            null
        }
    }

    private fun findElementDocCommentOwner(element: PsiElement): LuaCatsCommentOwner? {
        if (element.parent is LuaNameRef
            && element.parent.parent is LuaFuncName
            && element.parent.parent.parent is LuaFuncDecl
        ) {
            return element.parent.parent.parent as LuaCatsCommentOwner
        } else if (element.parent is LuaNameRef
            && element.parent.parent is LuaLocalFuncDecl
        ) {
            return element.parent.parent as LuaCatsCommentOwner
        }
        return null
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

    override fun computeDocumentationHint(): String? {
        return LuaDocumentationRenderer.renderHintDocumentation(element)
    }

    override val navigatable: Navigatable?
        get() = element as? Navigatable

    override fun computeDocumentation(): DocumentationResult? {
        return DocumentationResult.documentation(
            LuaDocumentationRenderer.renderFullDocumentation(element) ?: return null
        )
    }
}

