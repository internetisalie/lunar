package net.internetisalie.lunar.analysis

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl

/**
 * Inspection that surfaces return type mismatches.
 */
class LuaReturnTypeMismatchInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val types = LuaTypesSnapshot.forFile(file)
        val errors = types.getErrors()

        for (error in errors) {
            // Only report if the error is related to a return statement or function signature
            val isReturnRelated = error.element is LuaFinalStatement || 
                                 error.element.parent is LuaFinalStatement ||
                                 error.element is LuaFuncDef || 
                                 error.element is LuaFuncDecl || 
                                 error.element is LuaLocalFuncDecl


            if (isReturnRelated) {
                val severity = when (error.severity) {
                    ErrorSeverity.ERROR -> com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR
                    ErrorSeverity.WARNING -> com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    ErrorSeverity.WEAK_WARNING -> com.intellij.codeInspection.ProblemHighlightType.WEAK_WARNING
                }
                holder.registerProblem(error.element, error.message, severity)
            }
        }

        return object : LuaVisitor() {}
    }
}
