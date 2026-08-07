package net.internetisalie.lunar.analysis

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * Surfaces the engine's *hypotheses* — incompatibilities where the expectation was synthesized from
 * usage rather than declared by the user (BUG-419).
 *
 * These are deliberately **not** inspection findings. Measured across four corpus members, 7 430 of
 * 7 433 assignability emissions were inferred-demand-vs-inferred-value: two of the engine's own
 * guesses disagreeing, which is evidence its model is incomplete rather than that the code is wrong.
 * Reporting those as problems is the engine arguing with itself and blaming the user.
 *
 * So there is no squiggle, no Problems-view entry and no error-stripe weight — the annotation is
 * silent, and exists only to hang an intention off the conflict site. A user who wants the engine to
 * have an opinion here can accept it and annotate; a user who does not is not interrupted.
 *
 * An [Annotator] rather than a `LocalInspectionTool` because per-problem severity is impossible from
 * an inspection: `ProblemHighlightType` has no custom entries and the platform applies custom
 * severities per-inspection through the profile's `level=`. A silent annotation is the only way to
 * offer a fix without also making a claim.
 */
class LuaTypeHypothesisAnnotator : Annotator {
    override fun annotate(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        // Once per file: the snapshot holds errors for the whole file, so annotating on every
        // element would re-register each one N times.
        if (element !is LuaFile) return

        val hypotheses =
            LuaTypesSnapshot
                .forFile(element)
                .getErrors()
                .filter { it.severity == ErrorSeverity.HYPOTHESIS }

        for (hypothesis in hypotheses) {
            val range = hypothesis.element.textRange ?: continue
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .withFix(LuaAnnotateForTypeCheckingFix(hypothesis.inferredValueType))
                .create()
        }
    }
}
