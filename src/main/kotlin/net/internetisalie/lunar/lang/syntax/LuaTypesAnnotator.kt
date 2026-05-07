package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.ElementError
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * Runs the type inference engine on each Lua file and surfaces type errors as IDE annotations.
 *
 * This annotator is called once per PSI element. We gate all work behind an `is LuaFile` check
 * so inference runs exactly once per file (the snapshot is cached by [LuaTypesSnapshot.forFile]).
 *
 * Phase 1: [LuaTypesSnapshot.getErrors] always returns an empty list, so this annotator is a
 * no-op in practice.  The wiring is in place so Phase 3 error-checking activates automatically.
 *
 * See: docs/requirements/spec/type/design/phase-1-api-contracts.md §9
 */
class LuaTypesAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is LuaFile) return

        val types = LuaTypesSnapshot.forFile(element)
        for (error in types.getErrors()) {
            holder.newAnnotation(error.toSeverity(), error.message)
                .range(error.element)
                .create()
        }
    }

    private fun ElementError.toSeverity(): HighlightSeverity = when (severity) {
        ErrorSeverity.ERROR -> HighlightSeverity.ERROR
        ErrorSeverity.WARNING -> HighlightSeverity.WARNING
        ErrorSeverity.WEAK_WARNING -> HighlightSeverity.WEAK_WARNING
    }
}
