package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.lexer.LuaTokenTypes
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.lang.psi.*

object LuaDocGenerator {

    fun isDocCommentEmpty(comment: LuaCatsComment?): Boolean {
        if (comment == null) return true
        return comment.aliasTagList.isEmpty() &&
                comment.asyncTagList.isEmpty() &&
                comment.castTagList.isEmpty() &&
                comment.classTagList.isEmpty() &&
                comment.deprecatedTagList.isEmpty() &&
                comment.descriptionList.isEmpty() &&
                comment.diagnosticTagList.isEmpty() &&
                comment.enumTagList.isEmpty() &&
                comment.fieldTagList.isEmpty() &&
                comment.genericTagList.isEmpty() &&
                comment.metaTagList.isEmpty() &&
                comment.moduleTagList.isEmpty() &&
                comment.nodiscardTagList.isEmpty() &&
                comment.operatorTagList.isEmpty() &&
                comment.overloadTagList.isEmpty() &&
                comment.packageTagList.isEmpty() &&
                comment.paramTagList.isEmpty() &&
                comment.privateTagList.isEmpty() &&
                comment.protectedTagList.isEmpty() &&
                comment.returnTagList.isEmpty() &&
                comment.seeTagList.isEmpty() &&
                comment.sourceTagList.isEmpty() &&
                comment.typeOptionList.isEmpty() &&
                comment.typeTagList.isEmpty() &&
                comment.varargTagList.isEmpty() &&
                comment.versionTagList.isEmpty()
    }

    fun createTemplate(project: Project, owner: LuaCommentOwner, indent: String): Template? {
        val templateManager = TemplateManager.getInstance(project)
        val template = templateManager.createTemplate("", "")
        template.isToReformat = true
        template.setToIndent(true)

        when (owner) {
            is LuaFuncDecl -> buildFuncTemplate(template, owner, indent)
            is LuaLocalFuncDecl -> buildLocalFuncTemplate(template, owner, indent)
            is LuaLocalVarDecl -> {
                if (isClassTable(owner)) {
                    buildClassTemplate(template, owner, indent)
                } else {
                    return null
                }
            }
            else -> return null
        }

        return template
    }

    private fun isClassTable(owner: LuaLocalVarDecl): Boolean {
        val names = owner.attNameList
        if (names.size != 1) return false
        val exprList = owner.exprList ?: return false
        if (exprList.exprList.size != 1) return false
        return exprList.exprList[0] is LuaTableConstructor
    }

    private fun buildFuncTemplate(template: Template, func: LuaFuncDecl, indent: String) {
        val params = LuaPsiImplUtil.getParameters(func.parList)
        val hasReturn = hasReturnStatement(func.block)
        buildSignatureTemplate(template, params, hasReturn, indent)
    }

    private fun buildLocalFuncTemplate(template: Template, func: LuaLocalFuncDecl, indent: String) {
        val params = LuaPsiImplUtil.getParameters(func.parList)
        val hasReturn = hasReturnStatement(func.block)
        buildSignatureTemplate(template, params, hasReturn, indent)
    }

    private fun hasReturnStatement(block: LuaBlock?): Boolean {
        if (block == null) return false
        var found = false
        val visitor = object : LuaRecursiveVisitor() {
            override fun visitElement(element: PsiElement) {
                if (found) return
                if (element is LuaFinalStatement) {
                    found = true
                    return
                }
                if (element is LuaFuncDef || element is LuaFuncDecl || element is LuaLocalFuncDecl) return
                super.visitElement(element)
            }
        }
        block.accept(visitor)
        return found
    }

    private fun buildClassTemplate(template: Template, decl: LuaLocalVarDecl, indent: String) {
        val name = decl.attNameList[0].nameRef.name ?: "name"
        template.addTextSegment("--- @class ")
        template.addVariable("CLASS_NAME", TextExpression(name), true)
        template.addTextSegment("\n")
    }

    private fun buildSignatureTemplate(template: Template, params: List<String>, hasReturn: Boolean, indent: String) {
        template.addTextSegment("--- ")
        template.addVariable("DESC", TextExpression("description"), true)
        template.addTextSegment("\n")

        for ((index, param) in params.withIndex()) {
            template.addTextSegment("--- @param $param ")
            val inferredType = inferTypeByName(param)
            template.addVariable("PARAM_TYPE_$index", TextExpression(inferredType), true)
            template.addTextSegment(" ")
            template.addVariable("PARAM_DESC_$index", TextExpression("description"), true)
            template.addTextSegment("\n")
        }

        if (hasReturn) {
            template.addTextSegment("--- @return ")
            template.addVariable("RETURN_TYPE", TextExpression("any"), true)
            template.addTextSegment(" ")
            template.addVariable("RETURN_DESC", TextExpression("description"), true)
            template.addTextSegment("\n")
        }
    }

    private fun inferTypeByName(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("name") || lower.contains("path") || lower.contains("text") -> "string"
            lower.contains("count") || lower.contains("index") || lower.contains("num") || lower.contains("width") || lower.contains("height") || lower.contains("x") || lower.contains("y") -> "number"
            lower.contains("is") || lower.contains("has") || lower.contains("can") || lower.contains("enabled") -> "boolean"
            lower.contains("func") || lower.contains("callback") || lower.contains("handler") -> "fun(): any"
            lower.contains("list") || lower.contains("items") || lower.contains("args") -> "any[]"
            lower.contains("opts") || lower.contains("options") || lower.contains("config") -> "table"
            else -> "any"
        }
    }
}
