package net.internetisalie.lunar.analysis

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity

/**
 * Inspection that surfaces return type mismatches.
 */
class LuaReturnTypeMismatchInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val types = LuaTypesSnapshot.forFile(file)
        val errors = types.getErrors()

        for (error in errors) {
            // Only report errors related to a return statement or function signature; the rest are
            // surfaced by LuaTypeAssignabilityInspection (see isReturnRelated).
            if (error.isReturnRelated()) {
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
