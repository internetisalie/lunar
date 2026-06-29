package net.internetisalie.lunar.lang.schema

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaComplianceChecker
import com.intellij.codeInspection.LocalInspectionToolSession
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.lang.psi.LuaFile

class LuaJsonSchemaComplianceInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        val file = holder.file
        if (file !is LuaFile) return PsiElementVisitor.EMPTY_VISITOR

        val service = JsonSchemaService.Impl.get(file.project)
        if (!service.isApplicableToFile(file.virtualFile)) return PsiElementVisitor.EMPTY_VISITOR
        val schemaObject = service.getSchemaObject(file.virtualFile) ?: return PsiElementVisitor.EMPTY_VISITOR

        val options = JsonComplianceCheckerOptions(false)

        return object : LuaVisitor() {
            override fun visitFile(file: com.intellij.psi.PsiFile) {
                if (file !is LuaFile) return
                val roots = LuaJsonLikePsiWalker.INSTANCE.getRoots(file)
                val checker = JsonSchemaComplianceChecker(schemaObject, holder, LuaJsonLikePsiWalker.INSTANCE, session, options)
                for (root in roots) {
                    checker.annotate(root)
                }
            }
        }
    }
}
