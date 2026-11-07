package net.internetisalie.lunar.corpus.audit

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import net.internetisalie.lunar.analysis.isReturnRelated
import net.internetisalie.lunar.lang.psi.types.ElementError
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/** Thrown when a revert leaves the file text changed; aborts the member (design §3.4 step 3). */
private class RevertMismatch(
    message: String,
) : IllegalStateException(message)

/** Maps an offset in the edited text back to the unedited text (design §3.4). */
internal data class Shift(
    val at: Int,
    val delta: Int,
) {
    fun map(offset: Int): Int = if (offset >= at) offset - delta else offset

    companion object {
        val NONE = Shift(Int.MAX_VALUE, 0)
    }
}

/** One reported error, as the inspections see it: severity, inspection, message, unedited range. */
internal data class ErrorKey(
    val severity: String,
    val rule: String,
    val message: String,
    val range: TextRange,
)

/** The errors of the edited file and of the declaring file at one point of a measurement. */
internal data class Observation(
    val edited: List<ErrorKey>,
    val declaring: List<ErrorKey>,
) {
    operator fun minus(other: Observation): Observation =
        Observation(multisetMinus(edited, other.edited), multisetMinus(declaring, other.declaring))

    private fun multisetMinus(
        keys: List<ErrorKey>,
        removed: List<ErrorKey>,
    ): List<ErrorKey> {
        val remaining = removed.groupingBy { it }.eachCount().toMutableMap()
        return keys.filter { key ->
            val count = remaining[key] ?: 0
            if (count > 0) remaining[key] = count - 1
            count == 0
        }
    }
}

/** The opened site: its file, document, unedited text and the text under [AuditSite.edit]. */
internal class OpenedSite(
    val site: AuditSite,
    val psiFile: PsiFile,
    val declaringFile: PsiFile?,
) {
    val document: Document =
        requireNotNull(
            PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile),
        ) { "no document for ${site.file}" }
    val original: String = document.text
    val span: String = document.getText(site.edit)
}

private data class Measured(
    val outcome: AuditOutcome,
    val delta: List<AuditEntry>,
)

/** MAINT-54 design §3.4: injects one wrong and one control edit, reverts each, and classifies the outcome. */
class AuditInjector(
    private val fixture: CodeInsightTestFixture,
) {
    /** Steps 1–4 run in one `try`: any exception but [RevertMismatch] is `FAILED`; that one aborts the member. */
    @Suppress("TooGenericExceptionCaught")
    fun measure(site: AuditSite): AuditRecord {
        var line = 0
        val measured =
            try {
                val opened = open(site)
                line = opened.document.getLineNumber(site.edit.startOffset) + 1
                measureOpened(opened)
            } catch (mismatch: RevertMismatch) {
                throw mismatch
            } catch (failure: Exception) {
                println(
                    "[type-audit] FAILED ${site.file}:${site.edit} ${failure.javaClass.simpleName}: ${failure.message}",
                )
                Measured(AuditOutcome.FAILED, emptyList())
            }
        return AuditRecord(site.cell, site.file, line, site.declaredType, site.wrong, measured.outcome, measured.delta)
    }

    internal fun outcome(
        site: AuditSite,
        delta: List<AuditEntry>,
        noise: List<AuditEntry>,
    ): AuditOutcome {
        if (noise.any { it.anchor in DIRECT }) return AuditOutcome.NOISY
        val direct = delta.filter { it.anchor in DIRECT }
        val expected = if (site.cell.shape == "return.mismatch") RETURN_RULE else ASSIGN_RULE
        return when {
            direct.any { it.rule == expected } -> AuditOutcome.REPORTED
            direct.isNotEmpty() -> AuditOutcome.REPORTED_OTHER_RULE
            site.cell.shape.startsWith("lenient.") -> AuditOutcome.LENIENT_BY_DESIGN
            else -> AuditOutcome.SILENT
        }
    }

    internal fun verifyReverted(
        document: Document,
        original: String,
    ) {
        if (document.text == original) return
        throw RevertMismatch("revert left the text changed (${document.textLength} vs ${original.length} chars)")
    }

    private fun open(site: AuditSite): OpenedSite {
        val psiFile = findPsi(site.file)
        return OpenedSite(site, psiFile, site.declFile?.let { findPsi(it) })
    }

    private fun findPsi(path: String): PsiFile {
        val virtualFile = requireNotNull(fixture.findFileInTempDir(path)) { "no temp file $path" }
        return requireNotNull(PsiManager.getInstance(fixture.project).findFile(virtualFile)) { "no PSI for $path" }
    }

    private fun measureOpened(opened: OpenedSite): Measured {
        val site = opened.site
        val baselineEdited = keys(opened.psiFile, Shift.NONE)
        val preexisting = baselineEdited.any { it.range.intersects(site.site) }
        if (preexisting) return Measured(AuditOutcome.SKIPPED_PREEXISTING, emptyList())
        val baseline = Observation(baselineEdited, opened.declaringFile?.let { keys(it, Shift.NONE) }.orEmpty())
        val wrong = apply(opened, site.wrong)
        val control = site.control?.let { apply(opened, it) } ?: baseline
        val noise = anchored(site, control - baseline)
        val delta = anchored(site, (wrong - baseline) - (control - baseline))
        return Measured(outcome(site, delta, noise), delta)
    }

    /** Replaces the edit with [replacement], reads both files' errors, then writes the span back. */
    private fun apply(
        opened: OpenedSite,
        replacement: String,
    ): Observation {
        val edit = opened.site.edit
        replace(opened.document, edit, replacement)
        try {
            val shift = Shift(edit.startOffset + replacement.length, replacement.length - opened.span.length)
            return Observation(
                keys(opened.psiFile, shift),
                opened.declaringFile?.let { keys(it, Shift.NONE) }.orEmpty(),
            )
        } finally {
            replace(opened.document, TextRange.from(edit.startOffset, replacement.length), opened.span)
            verifyReverted(opened.document, opened.original)
        }
    }

    private fun replace(
        document: Document,
        range: TextRange,
        text: String,
    ) {
        WriteCommandAction.runWriteCommandAction(fixture.project) {
            document.replaceString(range.startOffset, range.endOffset, text)
            PsiDocumentManager.getInstance(fixture.project).commitDocument(document)
        }
    }

    private fun anchored(
        site: AuditSite,
        observation: Observation,
    ): List<AuditEntry> =
        observation.edited.map { it.entry(anchorOf(site, it.range)) } +
            observation.declaring.map { it.entry(Anchor.DECLARING_FILE) }

    private fun anchorOf(
        site: AuditSite,
        range: TextRange,
    ): Anchor =
        when {
            site.site.contains(range) -> Anchor.AT_SITE
            site.paramRange?.contains(range) == true -> Anchor.AT_PARAM
            range == site.declRange -> Anchor.AT_DECL
            else -> Anchor.BODY
        }

    private fun ErrorKey.entry(anchor: Anchor): AuditEntry = AuditEntry(severity, rule, message, anchor)

    internal companion object {
        const val RETURN_RULE = "LuaReturnTypeMismatch"
        const val ASSIGN_RULE = "LuaTypeAssignability"
        private val DIRECT = setOf(Anchor.AT_SITE, Anchor.AT_PARAM, Anchor.AT_DECL)

        /** The errors both type inspections report, `HYPOTHESIS` dropped, ranges mapped through [shift]. */
        fun keys(
            file: PsiFile,
            shift: Shift,
        ): List<ErrorKey> =
            LuaTypesSnapshot
                .forFile(file)
                .getErrors()
                .filter { it.severity != ErrorSeverity.HYPOTHESIS }
                .map { it.key(shift) }

        private fun ElementError.key(shift: Shift): ErrorKey {
            val range = element.textRange
            val rule = if (isReturnRelated()) RETURN_RULE else ASSIGN_RULE
            val end = shift.map(range.endOffset)
            // A range starting inside a longer replacement can map past its own end; it is clamped onto it.
            return ErrorKey(severity.name, rule, message, TextRange(minOf(shift.map(range.startOffset), end), end))
        }
    }
}
