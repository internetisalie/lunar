package net.internetisalie.lunar.corpus.audit

import com.google.gson.GsonBuilder
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import net.internetisalie.lunar.lang.psi.types.ElementError
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import java.io.File
import java.util.SortedMap

/** One reported error of a family: the file relative to the copied root, its 1-based line, its message. */
data class ExtrasSample(
    val file: String,
    val line: Int,
    val message: String,
)

/** MAINT-56 design §4.1: `build/type-corpus/extras/<member>.json`, read by `tooling/corpus/type_corpus.py`. */
data class ExtrasPayload(
    val schema: Int = 1,
    val member: String,
    val commit: String,
    val files: Int,
    val baseline: SortedMap<String, Int>,
    val samples: SortedMap<String, List<ExtrasSample>>,
)

/** A classified error, with where it sits, so samples sort by file then offset (design §3.8). */
private data class Finding(
    val family: String,
    val file: String,
    val offset: Int,
    val sample: ExtrasSample,
)

/**
 * MAINT-56 design §3.2: the error families the type inspections report on an unedited member — the
 * suspected false positives of design §3.5 — and a sample of each (§3.8).
 */
object TypeAuditExtras {
    val FAMILIES = listOf("arity.tooMany", "arity.tooFew", "field.missing", "return.mismatch", "assign.other")

    private const val SAMPLES_PER_FAMILY = 10
    private val GSON = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    /** The source of `AuditInjector.keys`: every error but `HYPOTHESIS`, counted per family. */
    fun baselineFamilies(file: LuaFile): Map<String, Int> =
        reported(file).groupingBy { family(it) }.eachCount().toSortedMap()

    /** First match wins; rule 4 tests the `return` statement itself, not `isReturnRelated()` (DR-07). */
    internal fun family(error: ElementError): String =
        when {
            error.message.startsWith("Too many arguments") -> "arity.tooMany"
            error.message.startsWith("Too few arguments") -> "arity.tooFew"
            error.message.startsWith("Missing required field") -> "field.missing"
            PsiTreeUtil.getParentOfType(
                error.element,
                LuaFinalStatement::class.java,
                false,
            ) != null -> "return.mismatch"
            else -> "assign.other"
        }

    fun payload(
        member: String,
        commit: String,
        files: List<LuaFile>,
    ): ExtrasPayload {
        val findings = files.flatMap { findings(it) }.sortedWith(compareBy({ it.file }, { it.offset }))
        val counts = findings.groupingBy { it.family }.eachCount()
        val baseline = FAMILIES.associateWith { counts[it] ?: 0 }.toSortedMap()
        val samples =
            FAMILIES
                .associateWith { family ->
                    findings.filter { it.family == family }.take(SAMPLES_PER_FAMILY).map { it.sample }
                }.toSortedMap()
        return ExtrasPayload(
            member = member,
            commit = commit,
            files = files.size,
            baseline = baseline,
            samples = samples,
        )
    }

    fun render(payload: ExtrasPayload): String = GSON.toJson(payload) + "\n"

    fun writeTo(
        directory: File,
        member: String,
        payload: ExtrasPayload,
    ) {
        directory.mkdirs()
        File(directory, "$member.json").writeText(render(payload), Charsets.UTF_8)
    }

    private fun reported(file: LuaFile): List<ElementError> =
        LuaTypesSnapshot
            .forFile(file)
            .getErrors()
            .filter { it.severity != ErrorSeverity.HYPOTHESIS }

    private fun findings(file: LuaFile): List<Finding> {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return emptyList()
        val path = AuditPaths.relativePath(file)
        return reported(file).map { error ->
            val offset = error.element.textRange.startOffset
            Finding(family(error), path, offset, ExtrasSample(path, document.getLineNumber(offset) + 1, error.message))
        }
    }
}
