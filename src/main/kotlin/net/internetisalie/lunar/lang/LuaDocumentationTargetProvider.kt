package net.internetisalie.lunar.lang

import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.createSmartPointer
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.luacats.lang.doc.LuaCatsDocumentationBuilder
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner

class LuaDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        var element = file.findElementAt(offset) ?: return emptyList()
        val originalElement = element
        if (originalElement.elementType == LuaElementTypes.IDENTIFIER && originalElement.parent is LuaFuncName) {
            val funcName : LuaFuncName = originalElement.parent as LuaFuncName
            val funcDecl : LuaFuncDecl = funcName.parent as LuaFuncDecl
            element = funcDecl
        }
        return arrayListOf(LuaDocumentationTarget(element, originalElement))
    }
}

class LuaPsiDocumentationTargetProvider : PsiDocumentationTargetProvider {
    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        val elementWithDocumentation = element.navigationElement ?: element
        return if (elementWithDocumentation.language.`is`(LuaLanguage)) LuaDocumentationTarget(elementWithDocumentation, originalElement) else null
    }
}

class LuaDocumentationTarget(val element : PsiElement, private val originalElement: PsiElement?) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val elementPtr = element.createSmartPointer()
        val originalElementPtr = originalElement?.createSmartPointer()
        return Pointer {
            val element = elementPtr.dereference() ?: return@Pointer null
            LuaDocumentationTarget(element, originalElementPtr?.dereference())
        }
    }

    override fun computePresentation(): TargetPresentation {
        return targetPresentation(element)
    }

    override fun computeDocumentationHint(): String? {
        // TODO: computeLuaCatsHintDocumentation(element)
        return computeLuaCatsFullDocumentation(element)
    }

    override val navigatable: Navigatable?
        get() = element as? Navigatable

    override fun computeDocumentation(): DocumentationResult? {
        val html = computeLuaCatsFullDocumentation(element) ?: return null
        return DocumentationResult.documentation(html)
    }
}

val docCommentHeader = """
<html>
    <head>    
        <style type="text/css">
            #error {
                background-color: #eeeeee;            
                margin-bottom: 10px;        
            }        
            .body {
               text-indent: 20px;
               margin-bottom: 5px;
            }
        </style>
    </head>
    <body>
""".trimIndent()

val docCommentFooter = """
</body></html>
""".trimIndent()

fun computeLuaCatsFullDocumentation(element: PsiElement): String? {
    if (element !is LuaCatsCommentOwner) return null
    val comment : LuaCatsComment = element.catsComment ?: return null

    val sb = StringBuilder()
    sb.append(docCommentHeader)

    when (element) {
        is LuaFuncDecl -> LuaCatsDocumentationBuilder.buildLuaFuncDecl(comment, element, sb)
    }

    sb.append(docCommentFooter)

    return sb.toString()
}
