package net.internetisalie.lunar.analysis

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * Inspection that surfaces type errors from the inference engine's constraint graph.
 * It queries the [LuaTypesSnapshot] for the file and reports detected [ElementError]s.
 */
class LuaTypeAssignabilityInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor {
        val file = holder.file
        val types = LuaTypesSnapshot.forFile(file)
        val errors = types.getErrors()

        // Report all non-return errors found in the graph (return-related errors are surfaced by
        // LuaReturnTypeMismatchInspection; the two partitions are exact complements so each error is
        // reported exactly once — see isReturnRelated).
        for (error in errors) {
            if (!error.isReturnRelated()) {
                // BUG-419: a hypothesis is not an inspection finding — it is the engine's guess
                // conflicting with its own guess. LuaTypeHypothesisAnnotator offers the annotate-it
                // intention instead, with no visible highlight.
                if (error.severity == ErrorSeverity.HYPOTHESIS) continue
                val severity =
                    when (error.severity) {
                        ErrorSeverity.ERROR -> ProblemHighlightType.GENERIC_ERROR
                        ErrorSeverity.WARNING -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                        ErrorSeverity.WEAK_WARNING -> ProblemHighlightType.WEAK_WARNING
                        ErrorSeverity.HYPOTHESIS -> continue
                    }
                holder.registerProblem(error.element, error.message, severity)
            }
        }

        // Return a no-op visitor since we already registered problems from the graph
        return object : LuaVisitor() {}
    }
}
