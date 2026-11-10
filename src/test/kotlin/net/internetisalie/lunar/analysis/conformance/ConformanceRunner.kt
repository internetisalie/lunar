package net.internetisalie.lunar.analysis.conformance

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import net.internetisalie.lunar.corpus.CorpusTarget
import net.internetisalie.lunar.corpus.SavedTarget
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.psi.types.ElementError
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * Runs one case in the light fixture (MAINT-55 design §3.4). Every case is configured under the one
 * name [FILE_NAME], so a case never sees the classes of the cases before it (risks-and-gaps DR-04).
 */
object ConformanceRunner {
    const val FILE_NAME = "conformance.lua"

    /** Pins the project to Standard 5.4, whatever target it held (DR-03, BUG-515). */
    fun pin(project: Project): SavedTarget = CorpusTarget.pin(project, LuaLanguageLevel.LUA54)

    /** The snapshot's non-hypothesis errors — what the two type inspections show — as line spans. */
    fun findings(
        fixture: CodeInsightTestFixture,
        case: ConformanceCase,
    ): List<ConformanceFinding> {
        val caseFile = fixture.configureByText(FILE_NAME, case.text)
        return runReadAction {
            val document =
                checkNotNull(PsiDocumentManager.getInstance(caseFile.project).getDocument(caseFile)) {
                    "no document for case ${case.id}"
                }
            LuaTypesSnapshot
                .forFile(caseFile)
                .getErrors()
                .filter { it.severity != ErrorSeverity.HYPOTHESIS }
                .map { finding(document, it) }
        }
    }

    private fun finding(
        document: Document,
        error: ElementError,
    ): ConformanceFinding {
        val range = error.element.textRange
        val endOffset = if (range.isEmpty) range.startOffset else range.endOffset - 1
        return ConformanceFinding(
            document.getLineNumber(range.startOffset) + 1,
            document.getLineNumber(endOffset) + 1,
            error.message,
        )
    }
}
